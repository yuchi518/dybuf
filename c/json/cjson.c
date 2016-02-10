/*
 * dybuf, dynamic buffer library
 * Copyright (C) 2015-2016 Yuchi (yuchi518@gmail.com)

 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation. For the terms of this
 * license, see <http://www.gnu.org/licenses>.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

//
// Created by Yuchi on 2016/2/6.
//

#include <string.h>
#include "cjson.h"

/// ==== json yacc & lex
#include "json.lex.c"




/// ============= cjson obj

static unsigned int memory_profile_size = 0;
static unsigned int* memory_alloc_records_idx;
static unsigned int* memory_release_records_idx;
static void** memory_alloc_records;
static void** memory_release_records;
void cjson_memory_profile(void** alloc_record, unsigned int* alloc_idx, void** release_record, unsigned int* release_idx, unsigned int size)
{
    memory_profile_size = size;
    memory_alloc_records = alloc_record;
    memory_release_records = release_record;
    memory_alloc_records_idx = alloc_idx;
    memory_release_records_idx = release_idx;
    *memory_alloc_records_idx = 0;
    *memory_release_records_idx = 0;
}

static inline void* cjson_memory_allocate(unsigned int size) {
    void* mem = malloc(size);
    if (memory_profile_size && mem && (*memory_alloc_records_idx)<memory_profile_size) {
        memory_alloc_records[*memory_alloc_records_idx] = mem;
        ++(*memory_alloc_records_idx);
    }
    return mem;
}

static inline void cjson_memory_release(void *mem)
{
    if (mem) {
        if (memory_profile_size && (*memory_release_records_idx)<memory_profile_size) {
            memory_release_records[*memory_release_records_idx] = mem;
            ++(*memory_release_records_idx);
        }
        free(mem);
    }
}

static inline void cjson_memory_copy(void* dst, void* src, unsigned int size)
{
    memcpy(dst, src, size);
}

static inline void cjson_memory_move(void* dst, void* src, unsigned int size)
{
    memmove(dst, src, size);
}

/// ========== cjson =========

struct jsobj* cjson_make(enum jstype type)
{
    switch(type)
    {
        case jstype_nil: return cjson_make_nil();
        case jstype_bool: return cjson_make_bool(false);
        case jstype_int: return cjson_make_int(0);
        case jstype_double: return cjson_make_double(0);
        case jstype_string: return cjson_make_string("");
        case jstype_array: return cjson_make_array();
        case jstype_map: return cjson_make_map();
    }
    return cjson_make_nil();
}

enum jserr cjson_release(struct jsobj* obj)
{
    if (obj == NULL) return jserr_invalid_args;
    switch(obj->type)
    {
        case jstype_nil: cjson_release_nil(_obj2inst_n(obj));
        case jstype_bool: cjson_release_bool(_obj2inst_b(obj));
        case jstype_int: cjson_release_int(_obj2inst_i(obj));
        case jstype_double: cjson_release_double(_obj2inst_d(obj));
        case jstype_string: cjson_release_string(_obj2inst_s(obj));
        case jstype_array: cjson_release_array(_obj2inst_a(obj));
        case jstype_map: cjson_release_map(_obj2inst_m(obj));
    }
    return jserr_invalid_args;
}

struct jsobj* cjson_clone(struct jsobj* obj)
{
    if (obj == NULL) return cjson_make_nil();
    switch(obj->type)
    {
        case jstype_nil: return cjson_clone_nil(_obj2inst_n(obj));
        case jstype_bool: return cjson_clone_bool(_obj2inst_b(obj));
        case jstype_int: return cjson_clone_int(_obj2inst_i(obj));
        case jstype_double: return cjson_clone_double(_obj2inst_d(obj));
        case jstype_string: return cjson_clone_string(_obj2inst_s(obj));
        case jstype_array: return cjson_clone_array(_obj2inst_a(obj));
        case jstype_map: return cjson_clone_map(_obj2inst_m(obj));
    }
    // TO-DO: print error
    obj->reference_count++;
    return  obj;
}

int cjson_compare(struct jsobj* obj0, struct jsobj* obj1)
{
    if (obj0==NULL && obj1==NULL) return 0;
    if (obj0==NULL) return -1;
    if (obj1==NULL) return 1;

    if (obj0->type == obj1->type)
    {
        switch(obj0->type)
        {
            case jstype_nil: return 0;
            case jstype_bool: return _obj2bool(obj0) - _obj2bool(obj1);
            case jstype_int: return _obj2int(obj0) - _obj2int(obj1);
            case jstype_double: {
                double d0 = _obj2double(obj0);
                double d1 = _obj2double(obj1);
                return d0==d1?0:d0>d1?1:-1;
            }
            case jstype_string: return strcmp(_obj2string(obj0), _obj2string(obj1));
            case jstype_array: return cjson_compare_array(_obj2inst_a(obj0), _obj2inst_a(obj1));
            case jstype_map: return cjson_compare_map(_obj2inst_m(obj0), _obj2inst_m(obj1));
        }
        // TO-DO: print error
        return (int)obj0 - (int)obj1;
    }
    else
    {
        return (int)obj0->type - (int)obj1->type;
    }
}

/// ========== nil ==========

struct jsobj* cjson_make_nil(void)
{
    struct jsobj_nil* obj = cjson_memory_allocate(sizeof(*obj));
    obj->base = (struct jsobj) {
        .type = jstype_nil,
        .should_copy = 0,
        .reference_count = 1,
        .wrapper = NULL,
        .this = NULL,
    };
    return &(obj->base);
}

struct jsobj* cjson_clone_nil(struct jsobj_nil* nil_obj)
{
    // TO-DO: use only one nil instance
    return cjson_make_nil();
}

enum jserr cjson_release_nil(struct jsobj_nil* nil_obj)
{
    if (nil_obj==NULL) return jserr_invalid_args;
    if ((--nil_obj->base.reference_count) <= 0)
        cjson_memory_release(nil_obj);
    return jserr_no_error;
}


/// ========== bool ==========

struct jsobj* cjson_make_bool(bool value)
{
    struct jsobj_bool* obj = cjson_memory_allocate(sizeof(*obj));
    obj->base = (struct jsobj) {
            .type = jstype_bool,
            .should_copy = 0,
            .reference_count = 1,
            .wrapper = NULL,
            .this = NULL,
    };
    obj->value = value;
    return &(obj->base);
}

struct jsobj* cjson_clone_bool(struct jsobj_bool* bool_obj)
{
    if (bool_obj == NULL) return cjson_make_nil();
    return cjson_make_bool(bool_obj->value);
}

enum jserr cjson_release_bool(struct jsobj_bool* bool_obj)
{
    if (bool_obj==NULL) return jserr_invalid_args;
    if ((--bool_obj->base.reference_count) <= 0)
        cjson_memory_release(bool_obj);
    return jserr_no_error;
}


/// ========== int ==========

struct jsobj* cjson_make_int(int value)
{
    struct jsobj_int* obj = cjson_memory_allocate(sizeof(*obj));
    obj->base = (struct jsobj) {
        .type = jstype_int,
        .should_copy = 0,
        .reference_count = 1,
        .wrapper = NULL,
        .this = NULL,
    };
    obj->value = value;
    return &(obj->base);
}

struct jsobj* cjson_clone_int(struct jsobj_int* int_obj)
{
    if (int_obj == NULL) return cjson_make_nil();
    return cjson_make_int(int_obj->value);
}

enum jserr cjson_release_int(struct jsobj_int* int_obj)
{
    if (int_obj==NULL) return jserr_invalid_args;
    if ((--int_obj->base.reference_count) <= 0)
        cjson_memory_release(int_obj);
    return jserr_no_error;
}

/// ========== double ==========

struct jsobj* cjson_make_double(double value)
{
    struct jsobj_double* obj = cjson_memory_allocate(sizeof(*obj));
    obj->base = (struct jsobj) {
            .type = jstype_double,
            .should_copy = 0,
            .reference_count = 1,
            .wrapper = NULL,
            .this = NULL,
    };
    obj->value = value;
    return &(obj->base);
}

struct jsobj* cjson_clone_double(struct jsobj_double* double_obj)
{
    if (double_obj == NULL) return cjson_make_nil();
    return cjson_make_double(double_obj->value);
}

enum jserr cjson_release_double(struct jsobj_double* double_obj)
{
    if (double_obj==NULL) return jserr_invalid_args;
    if ((--double_obj->base.reference_count) <= 0)
        cjson_memory_release(double_obj);
    return jserr_no_error;
}

/// ============= string ================
struct jsobj* cjson_make_string(char *value)
{
    unsigned int len = ((value==NULL)?0:strlen(value)) + 1;
    struct jsobj_string* obj = cjson_memory_allocate(sizeof(*obj));
    obj->base = (struct jsobj) {
            .type = jstype_string,
            .should_copy = 0,
            .reference_count = 1,
            .wrapper = NULL,
            .this = NULL,
    };
    obj->value = cjson_memory_allocate(len);
    strncpy(obj->value, value, len);
    return &(obj->base);
}

struct jsobj* cjson_clone_string(struct jsobj_string* string_obj)
{
    if (string_obj == NULL) return cjson_make_nil();
    return cjson_make_string(string_obj->value);
}

enum jserr cjson_release_string(struct jsobj_string* string_obj)
{
    if (string_obj==NULL) return jserr_invalid_args;
    if ((--string_obj->base.reference_count) <= 0) {
        cjson_memory_release(string_obj->value);
        cjson_memory_release(string_obj);
    }
    return jserr_no_error;
}

/// ========== array ==========

struct jsobj* cjson_make_array(void)
{
    const unsigned int capacity = 16;
    struct jsobj_array* obj = cjson_memory_allocate(sizeof(*obj));
    obj->base = (struct jsobj) {
            .type = jstype_array,
            .should_copy = 0,
            .reference_count = 1,
            .wrapper = NULL,
            .this = NULL,
    };
    obj->values = cjson_memory_allocate(sizeof(obj->values[0]) * capacity);
    obj->capacity = capacity;
    obj->size = 0;
    return &(obj->base);
}

struct jsobj* cjson_clone_array(struct jsobj_array* array_obj)
{
    if (array_obj == NULL) return cjson_make_nil();
    const unsigned int capacity = array_obj->capacity;
    struct jsobj_array* obj = cjson_memory_allocate(sizeof(*obj));
    obj->base = (struct jsobj) {
            .type = jstype_array,
            .should_copy = 0,
            .reference_count = 1,
            .wrapper = NULL,
            .this = NULL,
    };
    obj->values = cjson_memory_allocate(sizeof(obj->values[0]) * capacity);
    obj->capacity = capacity;
    obj->size = array_obj->size;

    unsigned int i;
    for (i=0; i<obj->size; i++)
    {
        obj->values[i] = cjson_clone(array_obj->values[i]);
    }

    return &(obj->base);
}

int cjson_compare_array(struct jsobj_array* array0, struct jsobj_array* array1)
{
    unsigned int size0 = array0->size,
                 size1 = array1->size;
    unsigned int i;
    if (size0==0 && size1==0) return 0;

    for (i=0; i<size0&&i<size1; i++)
    {
        unsigned r = cjson_compare(array0->values[i], array1->values[i]);
        if (r!=0) return r;
    }

    return (size0-size1);
}

enum jserr cjson_array_add_object(struct jsobj* array, struct jsobj* value)
{
    if (array==NULL || array->type!=jstype_array)
        return jserr_invalid_args;

    struct jsobj_array* obj = _obj2inst(array, struct jsobj_array);

    if (obj->size+1 >= obj->capacity)
    {
        const unsigned int capacity = 16 + obj->capacity;
        struct jsobj** values = cjson_memory_allocate(sizeof(values[0]) * capacity);
        if (values==NULL) return jserr_no_memory;
        cjson_memory_copy(values, obj->values, sizeof(values[0]) * obj->capacity);
        cjson_memory_release(obj->values);
        obj->values = values;
        obj->capacity = capacity;
    }

    obj->values[obj->size++] = cjson_clone(value);

    return jserr_no_error;
}

enum jserr cjson_release_array(struct jsobj_array* array_obj)
{
    if (array_obj==NULL) return jserr_invalid_args;

    if ((--array_obj->base.reference_count) <= 0)
    {
        unsigned int i = 0;
        for (i=0; i<array_obj->size; i++)
        {
            cjson_release(array_obj->values[i]);
        }
        cjson_memory_release(array_obj->values);
        cjson_memory_release(array_obj);
    }

    return jserr_no_error;
}

/// ========== map ==========

struct jsobj* cjson_make_map(void)
{
    const unsigned int capacity = 16;
    struct jsobj_map* obj = cjson_memory_allocate(sizeof(*obj));
    obj->base = (struct jsobj) {
            .type = jstype_map,
            .should_copy = 0,
            .reference_count = 1,
            .wrapper = NULL,
            .this = NULL,
    };
    obj->pairs = cjson_memory_allocate(sizeof(obj->pairs[0]) * capacity);
    obj->capacity = capacity;
    obj->size = 0;
    return &(obj->base);
}

struct jsobj* cjson_clone_map(struct jsobj_map* map_obj)
{
    if (map_obj==NULL) return cjson_make_nil();

    const unsigned int capacity = map_obj->capacity;
    struct jsobj_map* obj = cjson_memory_allocate(sizeof(*obj));
    obj->base = (struct jsobj) {
            .type = jstype_map,
            .should_copy = 0,
            .reference_count = 1,
            .wrapper = NULL,
            .this = NULL,
    };
    obj->pairs = cjson_memory_allocate(sizeof(obj->pairs[0]) * capacity);
    obj->capacity = capacity;
    obj->size = map_obj->size;

    unsigned int i;
    for (i=0; i<map_obj->size; i++)
    {
        obj->pairs[i].key = cjson_clone(map_obj->pairs[i].key);
        obj->pairs[i].value = cjson_clone(map_obj->pairs[i].value);
    }

    return &(obj->base);
}

int cjson_compare_map(struct jsobj_map* map0, struct jsobj_map* map1)
{
    unsigned int i;

    for (i=0; i<map0->size && i<map1->size; i++)
    {
        int r = cjson_compare(map0->pairs[i].key, map1->pairs[i].key);
        if (r!=0) return r;
    }

    if (map0->size == map1->size) return 0;
    if (map0->size > map1->size) return 1;
    else return -1;
}

static int compar_pair(const void* pair0,const void* pair1)
{
    return cjson_compare(((struct map_pair*)pair0)->key, ((struct map_pair*)pair1)->key);
}

enum jserr cjson_map_add_object(struct jsobj* map, struct jsobj* key, struct jsobj* value)
{
    if (map==NULL || map->type!=jstype_map)
        return jserr_invalid_args;

    struct jsobj_map* obj = _obj2inst(map, struct jsobj_map);

    unsigned int s = 0, e = obj->size, m = 0;
    int r = 0;

    while (s < e)
    {
        m = (s+e) >> 1;
        r = cjson_compare(key, obj->pairs[m].key);
        if (r < 0)
        {
            e = m-1;
        }
        else if (r > 0)
        {
            s = m+1;
        }
        else break;
    }

    if (s >= e)
    {
        // insert at s
        if (obj->size+1 > obj->capacity) {
            // create more space
            unsigned int capacity = obj->capacity+16;
            struct map_pair* pairs = cjson_memory_allocate(sizeof(pairs[0]) * capacity);
            if (pairs==NULL) return jserr_no_memory;
            if (s>0) cjson_memory_copy(pairs, obj->pairs, sizeof(pairs[0])*(s-1));
            pairs[s].key = cjson_clone(key);
            pairs[s].value = cjson_clone(value);
            if (s<obj->size) cjson_memory_copy(&pairs[s+1], &obj->pairs[s], sizeof(pairs[0])*(obj->size-s));
            cjson_memory_release(obj->pairs);
            obj->pairs = pairs;
            obj->capacity = capacity;
        } else {
            if (s<obj->size) cjson_memory_move(&obj->pairs[s+1], &obj->pairs[s], sizeof(obj->pairs[0])*(obj->size-s));
            obj->pairs[s].key = cjson_clone(key);
            obj->pairs[s].value = cjson_clone(value);
        }

        obj->size++;
    }
    else if (r==0)
    {
        // replace at m
        struct map_pair pair = obj->pairs[m];
        cjson_release(pair.key);
        cjson_release(pair.value);
        pair.key = cjson_clone(key);
        pair.value = cjson_clone(value);
        obj->pairs[m] = pair;
    }
    else
    {
        // impossible case
        return jserr_incorrect_map_alg;
    }

    return jserr_no_error;
}

enum jserr cjson_release_map(struct jsobj_map* map_obj)
{
    if (map_obj==NULL) return jserr_invalid_args;

    if ((--map_obj->base.reference_count) <= 0)
    {
        unsigned int i=0;
        for(i=0; i<map_obj->size; i++)
        {
            struct map_pair pair = map_obj->pairs[i];
            cjson_release(pair.key);
            cjson_release(pair.value);
        }
        cjson_memory_release(map_obj->pairs);
        cjson_memory_release(map_obj);
    }

    return jserr_no_error;
}

