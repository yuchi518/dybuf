#include <errno.h>
#include <inttypes.h>
#include <limits.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>

#include "../dybuf.h"

static int ensure_dir(const char *path) {
    char tmp[PATH_MAX];
    size_t len = strlen(path);
    if (len == 0 || len >= sizeof(tmp)) {
        return -1;
    }
    strcpy(tmp, path);

    for (size_t i = 1; i <= len; ++i) {
        if (tmp[i] == '/' || tmp[i] == '\0') {
            char saved = tmp[i];
            tmp[i] = '\0';
            if (tmp[0] != '\0') {
                if (mkdir(tmp, 0755) != 0 && errno != EEXIST) {
                    return -1;
                }
            }
            tmp[i] = saved;
        }
    }
    return 0;
}

static void bytes_to_hex(const uint8 *data, uint len, char *out) {
    for (uint i = 0; i < len; ++i) {
        sprintf(out + (i * 2), "%02x", data[i]);
    }
    out[len * 2] = '\0';
}

static void format_uint64_dec(uint64_t value, char *out, size_t out_size) {
    snprintf(out, out_size, "%" PRIu64, value);
}

static void format_uint64_hex(uint64_t value, char *out, size_t out_size) {
    snprintf(out, out_size, "0x%" PRIx64, value);
}

static void format_int64_dec(int64_t value, char *out, size_t out_size) {
    snprintf(out, out_size, "%" PRId64, value);
}

static void format_int64_hex(int64_t value, char *out, size_t out_size) {
    if (value < 0) {
        if (value == INT64_MIN) {
            snprintf(out, out_size, "-0x8000000000000000");
        } else {
            int64_t abs_val = -value;
            snprintf(out, out_size, "-0x%" PRIx64, (uint64_t)abs_val);
        }
    } else {
        snprintf(out, out_size, "0x%" PRIx64, (uint64_t)value);
    }
}

static void write_json_string(FILE *fp, const char *text) {
    fputc('"', fp);
    for (const unsigned char *p = (const unsigned char *)text; *p; ++p) {
        switch (*p) {
            case '"': fputs("\\\"", fp); break;
            case '\\': fputs("\\\\", fp); break;
            case '\b': fputs("\\b", fp); break;
            case '\f': fputs("\\f", fp); break;
            case '\n': fputs("\\n", fp); break;
            case '\r': fputs("\\r", fp); break;
            case '\t': fputs("\\t", fp); break;
            default:
                if (*p < 0x20) {
                    fprintf(fp, "\\u%04x", *p);
                } else {
                    fputc(*p, fp);
                }
        }
    }
    fputc('"', fp);
}

static int write_varuint(const char *out_dir) {
    static const uint64_t tier2_max = 0x3FFFULL + 0x80ULL;
    static const uint64_t tier3_max = 0x1FFFFFULL + 0x4080ULL;
    static const uint64_t tier4_max = 0x0FFFFFFFULL + 0x204080ULL;
    static const uint64_t tier5_max = 0x07FFFFFFFFULL + 0x10204080ULL;
    static const uint64_t tier6_max = 0x03FFFFFFFFFFULL + 0x0810204080ULL;
    static const uint64_t tier7_max = 0x01FFFFFFFFFFFFULL + 0x040810204080ULL;
    static const uint64_t tier8_max = 0x00FFFFFFFFFFFFFFULL + 0x02040810204080ULL;

    struct case_entry {
        const char *id;
        uint64_t value;
    } cases[] = {
        {"zero", 0},
        {"one", 1},
        {"tier1_max", 0x7FULL},
        {"tier2_min", 0x80ULL},
        {"tier2_max", tier2_max},
        {"tier3_min", tier2_max + 1ULL},
        {"tier3_max", tier3_max},
        {"tier4_min", tier3_max + 1ULL},
        {"tier4_max", tier4_max},
        {"tier5_min", tier4_max + 1ULL},
        {"tier5_max", tier5_max},
        {"tier6_min", tier5_max + 1ULL},
        {"tier6_max", tier6_max},
        {"tier7_min", tier6_max + 1ULL},
        {"tier7_max", tier7_max},
        {"tier8_min", tier7_max + 1ULL},
        {"tier8_max", tier8_max},
        {"u64_max", UINT64_MAX}
    };

    char path[PATH_MAX];
    snprintf(path, sizeof(path), "%s/varint_unsigned.json", out_dir);
    FILE *fp = fopen(path, "w");
    if (!fp) {
        perror("fopen varint_unsigned.json");
        return -1;
    }

    fprintf(fp, "{\n  \"cases\": [\n");

    dybuf buf_store;
    dybuf *buf = dyb_create(&buf_store, 32);
    if (!buf) {
        fclose(fp);
        return -1;
    }

    const size_t total = sizeof(cases) / sizeof(cases[0]);
    for (size_t i = 0; i < total; ++i) {
        dyb_clear(buf);
        dyb_append_var_u64(buf, cases[i].value);

        uint len = 0;
        uint8 *data = dyb_get_data_before_current_position(buf, &len);
        char *hex = (char *)malloc((len * 2) + 1);
        if (!hex) {
            dyb_release(buf);
            fclose(fp);
            return -1;
        }
        bytes_to_hex(data, len, hex);

        char value_dec[32];
        char value_hex[34];
        format_uint64_dec(cases[i].value, value_dec, sizeof(value_dec));
        format_uint64_hex(cases[i].value, value_hex, sizeof(value_hex));

        fprintf(fp,
                "    {\"id\":\"%s\",\"value_dec\":\"%s\",\"value_hex\":\"%s\",\"encoded_hex\":\"%s\"}%s\n",
                cases[i].id,
                value_dec,
                value_hex,
                hex,
                (i + 1 == total) ? "" : ",");
        free(hex);
    }

    dyb_release(buf);
    fprintf(fp, "  ]\n}\n");
    fclose(fp);
    return 0;
}

static int write_varint(const char *out_dir) {
    struct case_entry {
        const char *id;
        int64_t value;
    } cases[] = {
        {"zero", 0},
        {"one", 1},
        {"minus_one", -1},
        {"large_positive", 1234567890123456789LL},
        {"large_negative", -1234567890123456789LL},
        {"int64_max", INT64_MAX},
        {"int64_min", INT64_MIN}
    };

    char path[PATH_MAX];
    snprintf(path, sizeof(path), "%s/varint_signed.json", out_dir);
    FILE *fp = fopen(path, "w");
    if (!fp) {
        perror("fopen varint_signed.json");
        return -1;
    }

    fprintf(fp, "{\n  \"cases\": [\n");

    dybuf buf_store;
    dybuf *buf = dyb_create(&buf_store, 32);
    if (!buf) {
        fclose(fp);
        return -1;
    }

    const size_t total = sizeof(cases) / sizeof(cases[0]);
    for (size_t i = 0; i < total; ++i) {
        dyb_clear(buf);
        dyb_append_var_s64(buf, cases[i].value);

        uint len = 0;
        uint8 *data = dyb_get_data_before_current_position(buf, &len);
        char *hex = (char *)malloc((len * 2) + 1);
        if (!hex) {
            dyb_release(buf);
            fclose(fp);
            return -1;
        }
        bytes_to_hex(data, len, hex);

        char value_dec[32];
        char value_hex[35];
        format_int64_dec(cases[i].value, value_dec, sizeof(value_dec));
        format_int64_hex(cases[i].value, value_hex, sizeof(value_hex));

        fprintf(fp,
                "    {\"id\":\"%s\",\"value_dec\":\"%s\",\"value_hex\":\"%s\",\"encoded_hex\":\"%s\"}%s\n",
                cases[i].id,
                value_dec,
                value_hex,
                hex,
                (i + 1 == total) ? "" : ",");
        free(hex);
    }

    dyb_release(buf);
    fprintf(fp, "  ]\n}\n");
    fclose(fp);
    return 0;
}

static int write_typdex(const char *out_dir) {
    struct case_entry {
        const char *id;
        uint8_t type;
        uint32_t index;
    } cases[] = {
        {"tier1_zero", 0x00, 0x00},
        {"tier1_max", 0x0F, 0x07},
        {"tier2_min_type", 0x10, 0x00},
        {"tier2_max", 0x3F, 0xFF},
        {"tier3_min_type", 0x40, 0x0100},
        {"tier3_max", 0xFF, 0x1FFF},
        {"tier4_min_index", 0x01, 0x2000},
        {"tier4_max", 0xAA, 0x0FFFFF}
    };

    char path[PATH_MAX];
    snprintf(path, sizeof(path), "%s/typdex.json", out_dir);
    FILE *fp = fopen(path, "w");
    if (!fp) {
        perror("fopen typdex.json");
        return -1;
    }

    fprintf(fp, "{\n  \"cases\": [\n");

    dybuf buf_store;
    dybuf *buf = dyb_create(&buf_store, 16);
    if (!buf) {
        fclose(fp);
        return -1;
    }

    const size_t total = sizeof(cases) / sizeof(cases[0]);
    for (size_t i = 0; i < total; ++i) {
        dyb_clear(buf);
        dyb_append_typdex(buf, cases[i].type, cases[i].index);

        uint len = 0;
        uint8 *data = dyb_get_data_before_current_position(buf, &len);
        char *hex = (char *)malloc((len * 2) + 1);
        if (!hex) {
            dyb_release(buf);
            fclose(fp);
            return -1;
        }
        bytes_to_hex(data, len, hex);

        fprintf(fp,
                "    {\"id\":\"%s\",\"type\":%u,\"index\":%u,\"encoded_hex\":\"%s\"}%s\n",
                cases[i].id,
                (unsigned)cases[i].type,
                cases[i].index,
                hex,
                (i + 1 == total) ? "" : ",");
        free(hex);
    }

    dyb_release(buf);
    fprintf(fp, "  ]\n}\n");
    fclose(fp);
    return 0;
}

static int write_varlen_bytes(const char *out_dir) {
    struct case_entry {
        const char *id;
        const uint8_t *data;
        size_t length;
    };

    static const uint8_t bytes_small[] = {0x11, 0x22, 0x33};
    static const uint8_t bytes_medium[] = {
        0x00, 0x01, 0x7F, 0x80, 0xFF, 0x10, 0x20, 0x30,
        0x40, 0x50, 0x60, 0x70, 0x7A, 0x7B, 0x7C, 0x7D
    };

    const struct case_entry cases[] = {
        {"empty", NULL, 0},
        {"small", bytes_small, sizeof(bytes_small)},
        {"medium", bytes_medium, sizeof(bytes_medium)}
    };

    char path[PATH_MAX];
    snprintf(path, sizeof(path), "%s/varlen_bytes.json", out_dir);
    FILE *fp = fopen(path, "w");
    if (!fp) {
        perror("fopen varlen_bytes.json");
        return -1;
    }

    fprintf(fp, "{\n  \"cases\": [\n");

    dybuf buf_store;
    dybuf *buf = dyb_create(&buf_store, 128);
    if (!buf) {
        fclose(fp);
        return -1;
    }

    const size_t total = sizeof(cases) / sizeof(cases[0]);
    for (size_t i = 0; i < total; ++i) {
        dyb_clear(buf);
        dyb_append_data_with_var_len(buf, (uint8 *)cases[i].data, (uint)cases[i].length);

        uint len = 0;
        uint8 *encoded = dyb_get_data_before_current_position(buf, &len);
        char *encoded_hex = (char *)malloc((len * 2) + 1);
        if (!encoded_hex) {
            dyb_release(buf);
            fclose(fp);
            return -1;
        }
        bytes_to_hex(encoded, len, encoded_hex);

        char *payload_hex = (char *)malloc((cases[i].length * 2) + 1);
        if (!payload_hex) {
            free(encoded_hex);
            dyb_release(buf);
            fclose(fp);
            return -1;
        }
        if (cases[i].length > 0 && cases[i].data != NULL) {
            bytes_to_hex(cases[i].data, (uint)cases[i].length, payload_hex);
        } else {
            payload_hex[0] = '\0';
        }

        fprintf(fp,
                "    {\"id\":\"%s\",\"payload_hex\":\"%s\",\"payload_length\":%zu,\"encoded_hex\":\"%s\"}%s\n",
                cases[i].id,
                payload_hex,
                cases[i].length,
                encoded_hex,
                (i + 1 == total) ? "" : ",");

        free(encoded_hex);
        free(payload_hex);
    }

    dyb_release(buf);
    fprintf(fp, "  ]\n}\n");
    fclose(fp);
    return 0;
}

static int write_varlen_strings(const char *out_dir) {
    struct case_entry {
        const char *id;
        const char *text;
    };

    const struct case_entry cases[] = {
        {"empty", ""},
        {"hello_world", "hello world"},
        {"sentence", "dybuf fixture cross-language sample"}
    };

    char path[PATH_MAX];
    snprintf(path, sizeof(path), "%s/varlen_strings.json", out_dir);
    FILE *fp = fopen(path, "w");
    if (!fp) {
        perror("fopen varlen_strings.json");
        return -1;
    }

    fprintf(fp, "{\n  \"cases\": [\n");

    dybuf buf_store;
    dybuf *buf = dyb_create(&buf_store, 128);
    if (!buf) {
        fclose(fp);
        return -1;
    }

    const size_t total = sizeof(cases) / sizeof(cases[0]);
    for (size_t i = 0; i < total; ++i) {
        dyb_clear(buf);
        dyb_append_cstring_with_var_len(buf, cases[i].text);

        uint len = 0;
        uint8 *encoded = dyb_get_data_before_current_position(buf, &len);
        char *encoded_hex = (char *)malloc((len * 2) + 1);
        if (!encoded_hex) {
            dyb_release(buf);
            fclose(fp);
            return -1;
        }
        bytes_to_hex(encoded, len, encoded_hex);

        fprintf(fp, "    {\"id\":\"%s\",\"utf8\":", cases[i].id);
        write_json_string(fp, cases[i].text);
        fprintf(fp, ",\"encoded_hex\":\"%s\"}%s\n", encoded_hex, (i + 1 == total) ? "" : ",");

        free(encoded_hex);
    }

    dyb_release(buf);
    fprintf(fp, "  ]\n}\n");
    fclose(fp);
    return 0;
}

int main(int argc, char **argv) {
    const char *out_dir = argc > 1 ? argv[1] : "fixtures/v1";
    if (ensure_dir(out_dir) != 0) {
        fprintf(stderr, "Failed to create output directory: %s\n", out_dir);
        return EXIT_FAILURE;
    }

    if (write_varuint(out_dir) != 0) return EXIT_FAILURE;
    if (write_varint(out_dir) != 0) return EXIT_FAILURE;
    if (write_typdex(out_dir) != 0) return EXIT_FAILURE;
    if (write_varlen_bytes(out_dir) != 0) return EXIT_FAILURE;
    if (write_varlen_strings(out_dir) != 0) return EXIT_FAILURE;

    return EXIT_SUCCESS;
}
