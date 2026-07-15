#include <ctype.h>
#include <errno.h>
#include <inttypes.h>
#include <limits.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "../dybuf.h"

static int verbose_mode = 0;

typedef struct {
    const char *name;
    char *buffer;
    size_t buffer_size;
    int required;
    int seen;
} field_entry;

static void report_parse_error(const char *path, size_t case_index, const char *hint) {
    if (hint && *hint) {
        fprintf(stderr, "%s: failed to parse case #%zu (%s)\n", path, case_index, hint);
    } else {
        fprintf(stderr, "%s: failed to parse case #%zu\n", path, case_index);
    }
}

static void report_hex_error(const char *path, const char *field, const char *id, size_t case_index) {
    if (id && *id) {
        fprintf(stderr, "%s: invalid %s hex data (%s)\n", path, field, id);
    } else {
        fprintf(stderr, "%s: invalid %s hex data in case #%zu\n", path, field, case_index);
    }
}

static void report_decode_error(const char *path, const char *field, const char *id) {
    fprintf(stderr, "%s: %s mismatch (%s)\n", path, field, id);
}

static const char *skip_ws(const char *p) {
    while (*p && (*p == ' ' || *p == '\n' || *p == '\r' || *p == '\t')) {
        ++p;
    }
    return p;
}

static int parse_json_string(const char **cursor, char *out, size_t out_size) {
    const char *p = *cursor;
    if (*p != '"') return -1;
    ++p;
    size_t idx = 0;
    while (*p && *p != '"') {
        char ch;
        if (*p == '\\') {
            ++p;
            switch (*p) {
                case '"': ch = '"'; break;
                case '\\': ch = '\\'; break;
                case '/': ch = '/'; break;
                case 'b': ch = '\b'; break;
                case 'f': ch = '\f'; break;
                case 'n': ch = '\n'; break;
                case 'r': ch = '\r'; break;
                case 't': ch = '\t'; break;
                case 'u': {
                    int value = 0;
                    for (int i = 0; i < 4; ++i) {
                        ++p;
                        if (!*p) return -1;
                        char c = *p;
                        int digit;
                        if (c >= '0' && c <= '9') digit = c - '0';
                        else if (c >= 'a' && c <= 'f') digit = 10 + (c - 'a');
                        else if (c >= 'A' && c <= 'F') digit = 10 + (c - 'A');
                        else return -1;
                        value = (value << 4) | digit;
                    }
                    ch = (value <= 0x7F) ? (char)value : '?';
                    break;
                }
                case '\0': return -1;
                default: ch = *p; break;
            }
        } else {
            ch = *p;
        }

        if (idx + 1 >= out_size) return -1;
        out[idx++] = ch;
        ++p;
    }
    if (*p != '"') return -1;
    ++p;
    out[idx] = '\0';
    *cursor = p;
    return 0;
}

static int parse_json_number(const char **cursor, char *out, size_t out_size) {
    const char *p = *cursor;
    size_t idx = 0;
    while (*p && *p != ',' && *p != '}' && *p != ' ' && *p != '\n' && *p != '\r' && *p != '\t') {
        if (idx + 1 >= out_size) return -1;
        out[idx++] = *p;
        ++p;
    }
    out[idx] = '\0';
    *cursor = p;
    return 0;
}

static int skip_json_value(const char **cursor);

static int skip_json_string_value(const char **cursor) {
    char scratch[512];
    return parse_json_string(cursor, scratch, sizeof(scratch));
}

static int skip_json_compound(const char **cursor, char open_ch, char close_ch) {
    const char *p = *cursor;
    if (*p != open_ch) return -1;
    ++p;
    while (1) {
        p = skip_ws(p);
        if (*p == close_ch) {
            ++p;
            *cursor = p;
            return 0;
        }
        if (*p == '\0') return -1;
        if (open_ch == '{') {
            if (*p != '"') return -1;
            if (skip_json_string_value(&p) != 0) return -1;
            p = skip_ws(p);
            if (*p != ':') return -1;
            ++p;
        }
        p = skip_ws(p);
        if (skip_json_value(&p) != 0) return -1;
        p = skip_ws(p);
        if (*p == ',') {
            ++p;
            continue;
        }
        if (*p == close_ch) {
            ++p;
            *cursor = p;
            return 0;
        }
        return -1;
    }
}

static int skip_json_scalar(const char **cursor) {
    const char *p = *cursor;
    while (*p && *p != ',' && *p != '}' && *p != ']' && *p != ' ' && *p != '\n' && *p != '\r' && *p != '\t') {
        ++p;
    }
    if (p == *cursor) return -1;
    *cursor = p;
    return 0;
}

static int skip_json_value(const char **cursor) {
    const char *p = skip_ws(*cursor);
    if (*p == '"') {
        int status = skip_json_string_value(&p);
        *cursor = p;
        return status;
    }
    if (*p == '{') {
        int status = skip_json_compound(&p, '{', '}');
        *cursor = p;
        return status;
    }
    if (*p == '[') {
        int status = skip_json_compound(&p, '[', ']');
        *cursor = p;
        return status;
    }
    if (skip_json_scalar(&p) != 0) return -1;
    *cursor = p;
    return 0;
}

static int store_field(field_entry *fields, size_t field_count, const char *name, const char *value) {
    for (size_t i = 0; i < field_count; ++i) {
        if (strcmp(name, fields[i].name) == 0) {
            size_t len = strlen(value);
            if (len >= fields[i].buffer_size) return -1;
            memcpy(fields[i].buffer, value, len + 1);
            fields[i].seen = 1;
            return 0;
        }
    }
    return 0;
}

static int parse_json_object(const char **cursor, field_entry *fields, size_t field_count) {
    const char *p = *cursor;
    p = skip_ws(p);
    if (*p != '{') return -1;
    ++p;
    while (1) {
        p = skip_ws(p);
        if (*p == '}') {
            ++p;
            break;
        }
        if (*p != '"') return -1;

        char key[64];
        if (parse_json_string(&p, key, sizeof(key)) != 0) return -1;
        p = skip_ws(p);
        if (*p != ':') return -1;
        ++p;
        p = skip_ws(p);

        char value[512] = {0};
        if (*p == '"') {
            if (parse_json_string(&p, value, sizeof(value)) != 0) return -1;
        } else if (*p == '{' || *p == '[') {
            if (skip_json_value(&p) != 0) return -1;
            value[0] = '\0';
        } else {
            if (parse_json_number(&p, value, sizeof(value)) != 0) return -1;
        }

        if (store_field(fields, field_count, key, value) != 0) return -1;

        p = skip_ws(p);
        if (*p == ',') {
            ++p;
            continue;
        } else if (*p == '}') {
            ++p;
            break;
        } else {
            return -1;
        }
    }

    for (size_t i = 0; i < field_count; ++i) {
        if (fields[i].required && !fields[i].seen) return -1;
    }

    *cursor = p;
    return 0;
}

static int read_file(const char *path, char **buffer, size_t *length) {
    FILE *fp = fopen(path, "rb");
    if (!fp) {
        fprintf(stderr, "Failed to open %s: %s\n", path, strerror(errno));
        return -1;
    }
    if (fseek(fp, 0, SEEK_END) != 0) {
        fclose(fp);
        return -1;
    }
    long size = ftell(fp);
    if (size < 0) {
        fclose(fp);
        return -1;
    }
    if (fseek(fp, 0, SEEK_SET) != 0) {
        fclose(fp);
        return -1;
    }
    char *data = (char *)malloc((size_t)size + 1);
    if (!data) {
        fclose(fp);
        return -1;
    }
    size_t read_size = fread(data, 1, (size_t)size, fp);
    fclose(fp);
    if (read_size != (size_t)size) {
        free(data);
        return -1;
    }
    data[size] = '\0';
    *buffer = data;
    if (length) *length = (size_t)size;
    return 0;
}

static int hex_digit(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return 10 + (c - 'a');
    if (c >= 'A' && c <= 'F') return 10 + (c - 'A');
    return -1;
}

static int hex_to_bytes(const char *hex, uint8 **out_bytes, size_t *out_len) {
    size_t hex_len = strlen(hex);
    if (hex_len % 2 != 0) return -1;
    size_t len = hex_len / 2;
    if (out_len) *out_len = len;
    if (len == 0) {
        *out_bytes = NULL;
        return 0;
    }
    uint8 *buf = (uint8 *)malloc(len);
    if (!buf) return -1;
    for (size_t i = 0; i < len; ++i) {
        int hi = hex_digit(hex[i * 2]);
        int lo = hex_digit(hex[i * 2 + 1]);
        if (hi < 0 || lo < 0) {
            free(buf);
            return -1;
        }
        buf[i] = (uint8)((hi << 4) | lo);
    }
    *out_bytes = buf;
    return 0;
}

static const char *locate_cases_array(const char *data) {
    const char *p = strstr(data, "\"cases\"");
    if (!p) return NULL;
    p = strchr(p, '[');
    if (!p) return NULL;
    return p + 1;
}

static int compare_bytes(const uint8 *a, const uint8 *b, size_t len) {
    if (len == 0) return 0;
    return memcmp(a, b, len);
}

static dybuf *append_var_string(dybuf *buf, const char *text) {
    return dyb_append_data_with_var_len(buf, (uint8 *)text, (uint)strlen(text));
}

static dybuf *append_json_stream_header(dybuf *buf, uint64 dictionary_count) {
    dyb_append_typdex(buf, typdex_typ_obj, 0);
    dyb_append_var_u64(buf, 1);
    dyb_append_var_u64(buf, dictionary_count);
    return buf;
}

static dybuf *append_json_dictionary(dybuf *buf, const char *path, const char **keys, uint key_count) {
    append_var_string(buf, path);
    dyb_append_var_u64(buf, key_count);
    for (uint i = 0; i < key_count; ++i) {
        append_var_string(buf, keys[i]);
    }
    return buf;
}

static dybuf *append_json_payload_marker(dybuf *buf) {
    return dyb_append_typdex(buf, typdex_typ_obj, 1);
}

static dybuf *append_json_null(dybuf *buf, uint index) {
    return dyb_append_typdex(buf, typdex_typ_none, index);
}

static dybuf *append_json_bool(dybuf *buf, uint index, boolean value) {
    dyb_append_typdex(buf, typdex_typ_bool, index);
    return dyb_append_bool(buf, value);
}

static dybuf *append_json_int(dybuf *buf, uint index, int64 value) {
    dyb_append_typdex(buf, typdex_typ_int, index);
    return dyb_append_var_s64(buf, value);
}

static dybuf *append_json_uint(dybuf *buf, uint index, uint64 value) {
    dyb_append_typdex(buf, typdex_typ_uint, index);
    return dyb_append_var_u64(buf, value);
}

static dybuf *append_json_double(dybuf *buf, uint index, double value) {
    dyb_append_typdex(buf, typdex_typ_double, index);
    return dyb_append_double(buf, value);
}

static dybuf *append_json_string_value(dybuf *buf, uint index, const char *value) {
    dyb_append_typdex(buf, typdex_typ_string, index);
    return append_var_string(buf, value);
}

static dybuf *append_json_map_start(dybuf *buf, uint index, uint64 present_count) {
    dyb_append_typdex(buf, typdex_typ_map, index);
    return dyb_append_var_u64(buf, present_count);
}

static dybuf *append_json_array_start(dybuf *buf, uint index, uint64 item_count) {
    dyb_append_typdex(buf, typdex_typ_array, index);
    return dyb_append_var_u64(buf, item_count);
}

static void encode_json_root_object(dybuf *buf) {
    const char *root_keys[] = {"name", "version", "enabled"};
    append_json_stream_header(buf, 1);
    append_json_dictionary(buf, "$", root_keys, 3);
    append_json_payload_marker(buf);
    append_json_map_start(buf, 0, 3);
    append_json_string_value(buf, 0, "dybuf");
    append_json_uint(buf, 1, 1);
    append_json_bool(buf, 2, true);
}

static void encode_json_root_array_objects(dybuf *buf) {
    const char *array_keys[] = {"id", "name", "active"};
    append_json_stream_header(buf, 1);
    append_json_dictionary(buf, "$.[]", array_keys, 3);
    append_json_payload_marker(buf);
    append_json_array_start(buf, 0, 3);
    append_json_map_start(buf, 0, 2);
    append_json_uint(buf, 0, 1);
    append_json_string_value(buf, 1, "alpha");
    append_json_map_start(buf, 0, 2);
    append_json_uint(buf, 0, 2);
    append_json_bool(buf, 2, false);
    append_json_map_start(buf, 0, 2);
    append_json_string_value(buf, 1, "gamma");
    append_json_bool(buf, 2, true);
}

static void encode_json_nested_object(dybuf *buf) {
    const char *root_keys[] = {"meta", "items"};
    const char *meta_keys[] = {"title", "count"};
    const char *item_keys[] = {"coord"};
    const char *coord_keys[] = {"x", "y"};
    append_json_stream_header(buf, 4);
    append_json_dictionary(buf, "$", root_keys, 2);
    append_json_dictionary(buf, "$.0", meta_keys, 2);
    append_json_dictionary(buf, "$.1.[]", item_keys, 1);
    append_json_dictionary(buf, "$.1.[].0", coord_keys, 2);
    append_json_payload_marker(buf);
    append_json_map_start(buf, 0, 2);
    append_json_map_start(buf, 0, 2);
    append_json_string_value(buf, 0, "map");
    append_json_uint(buf, 1, 2);
    append_json_array_start(buf, 1, 2);
    append_json_map_start(buf, 0, 1);
    append_json_map_start(buf, 0, 2);
    append_json_int(buf, 0, -3);
    append_json_uint(buf, 1, 4);
    append_json_map_start(buf, 0, 1);
    append_json_map_start(buf, 0, 2);
    append_json_uint(buf, 0, 5);
    append_json_int(buf, 1, -6);
}

static void encode_json_mixed_array(dybuf *buf) {
    const char *object_keys[] = {"k"};
    append_json_stream_header(buf, 1);
    append_json_dictionary(buf, "$.[]", object_keys, 1);
    append_json_payload_marker(buf);
    append_json_array_start(buf, 0, 9);
    append_json_null(buf, 0);
    append_json_bool(buf, 0, true);
    append_json_bool(buf, 0, false);
    append_json_int(buf, 0, -7);
    append_json_uint(buf, 0, 42);
    append_json_double(buf, 0, 3.5);
    append_json_string_value(buf, 0, "text");
    append_json_map_start(buf, 0, 1);
    append_json_string_value(buf, 0, "v");
    append_json_array_start(buf, 0, 2);
    append_json_uint(buf, 0, 1);
    append_json_string_value(buf, 0, "two");
}

static void encode_json_root_scalar_string(dybuf *buf) {
    append_json_stream_header(buf, 0);
    append_json_payload_marker(buf);
    append_json_string_value(buf, 0, "hello");
}

static void encode_json_root_scalar_null(dybuf *buf) {
    append_json_stream_header(buf, 0);
    append_json_payload_marker(buf);
    append_json_null(buf, 0);
}

static void (*json_encoder_for_id(const char *id))(dybuf *buf) {
    if (strcmp(id, "root_object") == 0) return encode_json_root_object;
    if (strcmp(id, "root_array_objects") == 0) return encode_json_root_array_objects;
    if (strcmp(id, "nested_object") == 0) return encode_json_nested_object;
    if (strcmp(id, "mixed_array") == 0) return encode_json_mixed_array;
    if (strcmp(id, "root_scalar_string") == 0) return encode_json_root_scalar_string;
    if (strcmp(id, "root_scalar_null") == 0) return encode_json_root_scalar_null;
    return NULL;
}

static int verify_varuint(const char *dir) {
    char path[PATH_MAX];
    snprintf(path, sizeof(path), "%s/varint_unsigned.json", dir);
    char *content = NULL;
    if (read_file(path, &content, NULL) != 0) return -1;

    const char *p = locate_cases_array(content);
    if (!p) {
        free(content);
        return -1;
    }

    size_t case_index = 0;
    int status = 0;
    while (1) {
        p = skip_ws(p);
        if (*p == ']') {
            break;
        }
        size_t current_case = case_index + 1;
        char id[64] = {0};
        char value_dec[64] = {0};
        char encoded_hex[128] = {0};
        field_entry fields[] = {
            {"id", id, sizeof(id), 1, 0},
            {"value_dec", value_dec, sizeof(value_dec), 1, 0},
            {"encoded_hex", encoded_hex, sizeof(encoded_hex), 1, 0}
        };
        if (parse_json_object(&p, fields, sizeof(fields)/sizeof(fields[0])) != 0) {
            report_parse_error(path, current_case, id);
            status = -1;
            break;
        }

        uint8 *bytes = NULL;
        size_t byte_len = 0;
        if (hex_to_bytes(encoded_hex, &bytes, &byte_len) != 0) {
            report_hex_error(path, "encoded_hex", id, current_case);
            status = -1;
            break;
        }

        dybuf reader_store;
        dybuf *reader = dyb_refer(&reader_store, bytes, (uint)byte_len, false);
        if (!reader) {
            fprintf(stderr, "%s: failed to create reader for case #%zu\n", path, current_case);
            free(bytes);
            status = -1;
            break;
        }
        uint64 decoded = dyb_next_var_u64(reader);
        if (decoded != strtoull(value_dec, NULL, 10)) {
            report_decode_error(path, "varint_unsigned", id);
            free(bytes);
            status = -1;
            break;
        }
        if (dyb_get_position(reader) != byte_len) {
            fprintf(stderr, "%s: varint_unsigned extra bytes (%s)\n", path, id);
            free(bytes);
            status = -1;
            break;
        }

        dybuf writer_store;
        dybuf *writer = dyb_create(&writer_store, (uint)(byte_len + 8));
        if (!writer) {
            free(bytes);
            status = -1;
            break;
        }
        dyb_append_var_u64(writer, decoded);
        uint encoded_len = 0;
        uint8 *encoded = dyb_get_data_before_current_position(writer, &encoded_len);
        if (encoded_len != byte_len || compare_bytes(encoded, bytes, byte_len) != 0) {
            fprintf(stderr, "%s: varint_unsigned re-encode mismatch (%s)\n", path, id);
            dyb_release(writer);
            free(bytes);
            status = -1;
            break;
        }
        dyb_release(writer);
        free(bytes);

        if (verbose_mode) {
            printf("%s: OK (%s)\n", path, id[0] ? id : "case");
        }

        ++case_index;
        p = skip_ws(p);
        if (*p == ',') {
            ++p;
            continue;
        } else if (*p == ']') {
            break;
        }
    }

    free(content);
    return status;
}

static int verify_varint(const char *dir) {
    char path[PATH_MAX];
    snprintf(path, sizeof(path), "%s/varint_signed.json", dir);
    char *content = NULL;
    if (read_file(path, &content, NULL) != 0) return -1;

    const char *p = locate_cases_array(content);
    if (!p) {
        free(content);
        return -1;
    }

    int status = 0;
    size_t case_index = 0;
    while (1) {
        p = skip_ws(p);
        if (*p == ']') break;

        size_t current_case = case_index + 1;
        char id[64] = {0};
        char value_dec[64] = {0};
        char encoded_hex[128] = {0};
        field_entry fields[] = {
            {"id", id, sizeof(id), 1, 0},
            {"value_dec", value_dec, sizeof(value_dec), 1, 0},
            {"encoded_hex", encoded_hex, sizeof(encoded_hex), 1, 0}
        };
        if (parse_json_object(&p, fields, sizeof(fields)/sizeof(fields[0])) != 0) {
            report_parse_error(path, current_case, id);
            status = -1;
            break;
        }

        uint8 *bytes = NULL;
        size_t byte_len = 0;
        if (hex_to_bytes(encoded_hex, &bytes, &byte_len) != 0) {
            report_hex_error(path, "encoded_hex", id, current_case);
            status = -1;
            break;
        }

        dybuf reader_store;
        dybuf *reader = dyb_refer(&reader_store, bytes, (uint)byte_len, false);
        if (!reader) {
            fprintf(stderr, "%s: failed to create reader for case #%zu\n", path, current_case);
            free(bytes);
            status = -1;
            break;
        }
        int64 decoded = dyb_next_var_s64(reader);
        if (decoded != strtoll(value_dec, NULL, 10)) {
            report_decode_error(path, "varint_signed", id);
            free(bytes);
            status = -1;
            break;
        }
        if (dyb_get_position(reader) != byte_len) {
            fprintf(stderr, "%s: varint_signed extra bytes (%s)\n", path, id);
            free(bytes);
            status = -1;
            break;
        }

        dybuf writer_store;
        dybuf *writer = dyb_create(&writer_store, (uint)(byte_len + 8));
        if (!writer) {
            free(bytes);
            status = -1;
            break;
        }
        dyb_append_var_s64(writer, decoded);
        uint encoded_len = 0;
        uint8 *encoded = dyb_get_data_before_current_position(writer, &encoded_len);
        if (encoded_len != byte_len || compare_bytes(encoded, bytes, byte_len) != 0) {
            fprintf(stderr, "%s: varint_signed re-encode mismatch (%s)\n", path, id);
            dyb_release(writer);
            free(bytes);
            status = -1;
            break;
        }
        dyb_release(writer);
        free(bytes);

        if (verbose_mode) {
            printf("%s: OK (%s)\n", path, id[0] ? id : "case");
        }

        ++case_index;
        p = skip_ws(p);
        if (*p == ',') {
            ++p;
            continue;
        } else if (*p == ']') {
            break;
        }
    }

    free(content);
    return status;
}

static int verify_typdex(const char *dir) {
    char path[PATH_MAX];
    snprintf(path, sizeof(path), "%s/typdex.json", dir);
    char *content = NULL;
    if (read_file(path, &content, NULL) != 0) return -1;

    const char *p = locate_cases_array(content);
    if (!p) {
        free(content);
        return -1;
    }

    int status = 0;
    size_t case_index = 0;
    while (1) {
        p = skip_ws(p);
        if (*p == ']') break;

        size_t current_case = case_index + 1;
        char id[64] = {0};
        char type_str[16] = {0};
        char index_str[32] = {0};
        char encoded_hex[128] = {0};
        field_entry fields[] = {
            {"id", id, sizeof(id), 1, 0},
            {"type", type_str, sizeof(type_str), 1, 0},
            {"index", index_str, sizeof(index_str), 1, 0},
            {"encoded_hex", encoded_hex, sizeof(encoded_hex), 1, 0}
        };
        if (parse_json_object(&p, fields, sizeof(fields)/sizeof(fields[0])) != 0) {
            report_parse_error(path, current_case, id);
            status = -1;
            break;
        }

        uint8 *bytes = NULL;
        size_t byte_len = 0;
        if (hex_to_bytes(encoded_hex, &bytes, &byte_len) != 0) {
            report_hex_error(path, "encoded_hex", id, current_case);
            status = -1;
            break;
        }

        dybuf reader_store;
        dybuf *reader = dyb_refer(&reader_store, bytes, (uint)byte_len, false);
        if (!reader) {
            fprintf(stderr, "%s: failed to create reader for case #%zu\n", path, current_case);
            free(bytes);
            status = -1;
            break;
        }
        uint8 type = 0;
        uint index = 0;
        dyb_next_typdex(reader, &type, &index);
        if (type != (uint8)strtoul(type_str, NULL, 10) || index != (uint)strtoul(index_str, NULL, 10)) {
            report_decode_error(path, "typdex", id);
            free(bytes);
            status = -1;
            break;
        }
        if (dyb_get_position(reader) != byte_len) {
            fprintf(stderr, "%s: typdex extra bytes (%s)\n", path, id);
            free(bytes);
            status = -1;
            break;
        }

        dybuf writer_store;
        dybuf *writer = dyb_create(&writer_store, (uint)(byte_len + 4));
        if (!writer) {
            free(bytes);
            status = -1;
            break;
        }
        dyb_append_typdex(writer, type, index);
        uint encoded_len = 0;
        uint8 *encoded = dyb_get_data_before_current_position(writer, &encoded_len);
        if (encoded_len != byte_len || compare_bytes(encoded, bytes, byte_len) != 0) {
            fprintf(stderr, "%s: typdex re-encode mismatch (%s)\n", path, id);
            dyb_release(writer);
            free(bytes);
            status = -1;
            break;
        }
        dyb_release(writer);
        free(bytes);

        if (verbose_mode) {
            printf("%s: OK (%s)\n", path, id[0] ? id : "case");
        }

        ++case_index;
        p = skip_ws(p);
        if (*p == ',') {
            ++p;
            continue;
        } else if (*p == ']') {
            break;
        }
    }

    free(content);
    return status;
}

static int verify_varlen_bytes(const char *dir) {
    char path[PATH_MAX];
    snprintf(path, sizeof(path), "%s/varlen_bytes.json", dir);
    char *content = NULL;
    if (read_file(path, &content, NULL) != 0) return -1;

    const char *p = locate_cases_array(content);
    if (!p) {
        free(content);
        return -1;
    }

    int status = 0;
    size_t case_index = 0;
    while (1) {
        p = skip_ws(p);
        if (*p == ']') break;

        size_t current_case = case_index + 1;
        char id[64] = {0};
        char payload_hex[512] = {0};
        char payload_length_str[32] = {0};
        char encoded_hex[1024] = {0};
        field_entry fields[] = {
            {"id", id, sizeof(id), 1, 0},
            {"payload_hex", payload_hex, sizeof(payload_hex), 1, 0},
            {"payload_length", payload_length_str, sizeof(payload_length_str), 1, 0},
            {"encoded_hex", encoded_hex, sizeof(encoded_hex), 1, 0}
        };
        if (parse_json_object(&p, fields, sizeof(fields)/sizeof(fields[0])) != 0) {
            report_parse_error(path, current_case, id);
            status = -1;
            break;
        }

        uint8 *encoded_bytes = NULL;
        size_t encoded_len = 0;
        if (hex_to_bytes(encoded_hex, &encoded_bytes, &encoded_len) != 0) {
            report_hex_error(path, "encoded_hex", id, current_case);
            status = -1;
            break;
        }

        uint8 *payload_bytes = NULL;
        size_t payload_len = 0;
        if (hex_to_bytes(payload_hex, &payload_bytes, &payload_len) != 0) {
            report_hex_error(path, "payload_hex", id, current_case);
            free(encoded_bytes);
            status = -1;
            break;
        }

        uint expected_len = (uint)strtoul(payload_length_str, NULL, 10);
        if (expected_len != payload_len) {
            fprintf(stderr, "%s: varlen_bytes payload length mismatch (%s)\n", path, id);
            free(encoded_bytes);
            free(payload_bytes);
            status = -1;
            break;
        }

        dybuf reader_store;
        dybuf *reader = dyb_refer(&reader_store, encoded_bytes, (uint)encoded_len, false);
        if (!reader) {
            fprintf(stderr, "%s: failed to create reader for case #%zu\n", path, current_case);
            free(encoded_bytes);
            free(payload_bytes);
            status = -1;
            break;
        }
        uint actual_len = 0;
        uint8 *actual_payload = dyb_next_data_with_var_len(reader, &actual_len);
        if (actual_len != expected_len) {
            fprintf(stderr, "%s: varlen_bytes decoded length mismatch (%s)\n", path, id);
            free(encoded_bytes);
            free(payload_bytes);
            status = -1;
            break;
        }
        if (actual_len > 0 && compare_bytes(actual_payload, payload_bytes, actual_len) != 0) {
            fprintf(stderr, "%s: varlen_bytes payload mismatch (%s)\n", path, id);
            free(encoded_bytes);
            free(payload_bytes);
            status = -1;
            break;
        }
        if (dyb_get_position(reader) != encoded_len) {
            fprintf(stderr, "%s: varlen_bytes extra bytes (%s)\n", path, id);
            free(encoded_bytes);
            free(payload_bytes);
            status = -1;
            break;
        }

        dybuf writer_store;
        dybuf *writer = dyb_create(&writer_store, (uint)(encoded_len + 16));
        if (!writer) {
            free(encoded_bytes);
            free(payload_bytes);
            status = -1;
            break;
        }
        dyb_append_data_with_var_len(writer, payload_bytes, expected_len);
        uint out_len = 0;
        uint8 *out_bytes = dyb_get_data_before_current_position(writer, &out_len);
        if (out_len != encoded_len || compare_bytes(out_bytes, encoded_bytes, encoded_len) != 0) {
            fprintf(stderr, "%s: varlen_bytes re-encode mismatch (%s)\n", path, id);
            dyb_release(writer);
            free(encoded_bytes);
            free(payload_bytes);
            status = -1;
            break;
        }
        dyb_release(writer);

        free(encoded_bytes);
        free(payload_bytes);

        if (verbose_mode) {
            printf("%s: OK (%s)\n", path, id[0] ? id : "case");
        }

        ++case_index;
        p = skip_ws(p);
        if (*p == ',') {
            ++p;
            continue;
        } else if (*p == ']') {
            break;
        }
    }

    free(content);
    return status;
}

static int verify_varlen_strings(const char *dir) {
    char path[PATH_MAX];
    snprintf(path, sizeof(path), "%s/varlen_strings.json", dir);
    char *content = NULL;
    if (read_file(path, &content, NULL) != 0) return -1;

    const char *p = locate_cases_array(content);
    if (!p) {
        free(content);
        return -1;
    }

    int status = 0;
    size_t case_index = 0;
    while (1) {
        p = skip_ws(p);
        if (*p == ']') break;

        size_t current_case = case_index + 1;
        char id[64] = {0};
        char utf8[512] = {0};
        char encoded_hex[1024] = {0};
        field_entry fields[] = {
            {"id", id, sizeof(id), 1, 0},
            {"utf8", utf8, sizeof(utf8), 1, 0},
            {"encoded_hex", encoded_hex, sizeof(encoded_hex), 1, 0}
        };
        if (parse_json_object(&p, fields, sizeof(fields)/sizeof(fields[0])) != 0) {
            report_parse_error(path, current_case, id);
            status = -1;
            break;
        }

        uint8 *encoded_bytes = NULL;
        size_t encoded_len = 0;
        if (hex_to_bytes(encoded_hex, &encoded_bytes, &encoded_len) != 0) {
            report_hex_error(path, "encoded_hex", id, current_case);
            status = -1;
            break;
        }

        dybuf reader_store;
        dybuf *reader = dyb_refer(&reader_store, encoded_bytes, (uint)encoded_len, false);
        if (!reader) {
            fprintf(stderr, "%s: failed to create reader for case #%zu\n", path, current_case);
            free(encoded_bytes);
            status = -1;
            break;
        }
        uint str_len = 0;
        char *decoded = dyb_next_cstring_with_var_len(reader, &str_len);
        if (!decoded) {
            fprintf(stderr, "%s: varlen_strings decode failure (%s)\n", path, id);
            free(encoded_bytes);
            status = -1;
            break;
        }
        if (strcmp(decoded, utf8) != 0) {
            fprintf(stderr, "%s: varlen_strings mismatch (%s)\n", path, id);
            free(encoded_bytes);
            status = -1;
            break;
        }
        if (dyb_get_position(reader) != encoded_len) {
            fprintf(stderr, "%s: varlen_strings extra bytes (%s)\n", path, id);
            free(encoded_bytes);
            status = -1;
            break;
        }

        dybuf writer_store;
        dybuf *writer = dyb_create(&writer_store, (uint)(encoded_len + 16));
        if (!writer) {
            free(encoded_bytes);
            status = -1;
            break;
        }
        dyb_append_cstring_with_var_len(writer, utf8);
        uint out_len = 0;
        uint8 *out_bytes = dyb_get_data_before_current_position(writer, &out_len);
        if (out_len != encoded_len || compare_bytes(out_bytes, encoded_bytes, encoded_len) != 0) {
            fprintf(stderr, "%s: varlen_strings re-encode mismatch (%s)\n", path, id);
            dyb_release(writer);
            free(encoded_bytes);
            status = -1;
            break;
        }
        dyb_release(writer);
        free(encoded_bytes);

        if (verbose_mode) {
            printf("%s: OK (%s)\n", path, id[0] ? id : "case");
        }

        ++case_index;
        p = skip_ws(p);
        if (*p == ',') {
            ++p;
            continue;
        } else if (*p == ']') {
            break;
        }
    }

    free(content);
    return status;
}

static int verify_json_values(const char *dir) {
    char path[PATH_MAX];
    snprintf(path, sizeof(path), "%s/json_values.json", dir);
    char *content = NULL;
    if (read_file(path, &content, NULL) != 0) return -1;

    const char *p = locate_cases_array(content);
    if (!p) {
        free(content);
        return -1;
    }

    int status = 0;
    size_t case_index = 0;
    while (1) {
        p = skip_ws(p);
        if (*p == ']') break;

        size_t current_case = case_index + 1;
        char id[64] = {0};
        char encoded_hex[2048] = {0};
        field_entry fields[] = {
            {"id", id, sizeof(id), 1, 0},
            {"encoded_hex", encoded_hex, sizeof(encoded_hex), 1, 0}
        };
        if (parse_json_object(&p, fields, sizeof(fields)/sizeof(fields[0])) != 0) {
            report_parse_error(path, current_case, id);
            status = -1;
            break;
        }

        void (*encoder)(dybuf *buf) = json_encoder_for_id(id);
        if (!encoder) {
            fprintf(stderr, "%s: unknown JSON fixture id (%s)\n", path, id);
            status = -1;
            break;
        }

        uint8 *encoded_bytes = NULL;
        size_t encoded_len = 0;
        if (hex_to_bytes(encoded_hex, &encoded_bytes, &encoded_len) != 0) {
            report_hex_error(path, "encoded_hex", id, current_case);
            status = -1;
            break;
        }

        dybuf writer_store;
        dybuf *writer = dyb_create(&writer_store, (uint)(encoded_len + 64));
        if (!writer) {
            free(encoded_bytes);
            status = -1;
            break;
        }
        encoder(writer);

        uint out_len = 0;
        uint8 *out_bytes = dyb_get_data_before_current_position(writer, &out_len);
        if (out_len != encoded_len || compare_bytes(out_bytes, encoded_bytes, encoded_len) != 0) {
            fprintf(stderr, "%s: json_values re-encode mismatch (%s)\n", path, id);
            dyb_release(writer);
            free(encoded_bytes);
            status = -1;
            break;
        }

        dybuf reader_store;
        dybuf *reader = dyb_refer(&reader_store, encoded_bytes, (uint)encoded_len, false);
        if (!reader) {
            fprintf(stderr, "%s: failed to create reader for case #%zu\n", path, current_case);
            dyb_release(writer);
            free(encoded_bytes);
            status = -1;
            break;
        }
        uint8 type = 0;
        uint index = 0;
        dyb_next_typdex(reader, &type, &index);
        if (type != typdex_typ_obj || index != 0 || dyb_next_var_u64(reader) != 1) {
            fprintf(stderr, "%s: json_values invalid stream header (%s)\n", path, id);
            dyb_release(writer);
            free(encoded_bytes);
            status = -1;
            break;
        }

        dyb_release(writer);
        free(encoded_bytes);

        if (verbose_mode) {
            printf("%s: OK (%s)\n", path, id[0] ? id : "case");
        }

        ++case_index;
        p = skip_ws(p);
        if (*p == ',') {
            ++p;
            continue;
        } else if (*p == ']') {
            break;
        }
    }

    free(content);
    return status;
}

int main(int argc, char **argv) {
    const char *dir = "fixtures/v1";
    int arg_index = 1;

    if (arg_index < argc && strcmp(argv[arg_index], "-v") == 0) {
        verbose_mode = 1;
        ++arg_index;
    }
    if (arg_index < argc) {
        dir = argv[arg_index];
        ++arg_index;
    }
    if (arg_index < argc) {
        fprintf(stderr, "Usage: %s [-v] [fixtures_dir]\n", argv[0]);
        return EXIT_FAILURE;
    }

    if (verify_varuint(dir) != 0) return EXIT_FAILURE;
    if (verify_varint(dir) != 0) return EXIT_FAILURE;
    if (verify_typdex(dir) != 0) return EXIT_FAILURE;
    if (verify_varlen_bytes(dir) != 0) return EXIT_FAILURE;
    if (verify_varlen_strings(dir) != 0) return EXIT_FAILURE;
    if (verify_json_values(dir) != 0) return EXIT_FAILURE;

    printf("Fixture verification succeeded for %s\n", dir);
    return EXIT_SUCCESS;
}
