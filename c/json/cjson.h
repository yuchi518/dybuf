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
// Created by Yuchi Chen on 2016/2/6.
//

#ifndef DYBUF_C_CJSON_H
#define DYBUF_C_CJSON_H

#include "plat_type.h"

/*
 *  var message = "hello";
 *  var sayhi = lambda(name, count)
 *  {
 *      var i = 1;
 *      message.print()
 *      var myprint = lambda(fmt, ...)
 *      {
 *          //
 *      }
 *      while (expr)
 *      {
 *          //
 *      }
 *
 *      while (expr, lambda()) ;
 *
 *      foreach (value in array)
 *      {
 *          //
 *      }
 *
 *      foreach(array, lambda(value) { });
 *
 *      foreach (key, value in map)
 *      {
 *          //
 *      }
 *
 *      foreach(map, lambda(key, value) {});
 *  }
 *  sayhi("cyc", 1)
 */

enum jserr
{
    jserr_no_error          = 0,
    jserr_no_memory,
    jserr_invalid_args,
    jserr_incorrect_map_alg,
};

enum jstype
{
    jstype_nil          = 0x000,

    jstype_bool         = 0x010,

    jstype_int          = 0x020,        // 32bits or 64bits, depends on platform

    jstype_double       = 0x030,

    jstype_string       = 0x040,

    //jstype_object       = 0x100,
    jstype_array        = 0x101,
    jstype_map          = 0x102,

    //jstype_closure      = 0x200,
    //jstype_express,                     // expr is a closure with a return boolean value
    //jstype_lambda,                      // lambda is a functional closure with input/output values

};

struct jsobj
{
    enum jstype type;
    unsigned int should_copy:1;
    unsigned int reference_count;
    struct jsobj * wrapper;           // belong to which closure
    struct jsobj * this;
};

#define _obj2inst(ptr, type) ({ \
    const typeof( ((type *)0)->base ) *__mptr = (ptr); \
    (type *)( (void *)__mptr - ((size_t) &((type *)0)->base) );})
#define _obj2inst_n(ptr)    (_obj2inst(ptr, struct jsobj_nil) )
#define _obj2inst_b(ptr)    (_obj2inst(ptr, struct jsobj_bool) )
#define _obj2inst_i(ptr)    (_obj2inst(ptr, struct jsobj_int) )
#define _obj2inst_d(ptr)    (_obj2inst(ptr, struct jsobj_double) )
#define _obj2inst_s(ptr)    (_obj2inst(ptr, struct jsobj_string) )
#define _obj2inst_a(ptr)    (_obj2inst(ptr, struct jsobj_array) )
#define _obj2inst_m(ptr)    (_obj2inst(ptr, struct jsobj_map) )
#define _obj2bool(ptr)       ( _obj2inst(ptr, struct jsobj_bool)->value )
#define _obj2int(ptr)       ( _obj2inst(ptr, struct jsobj_int)->value )
#define _obj2double(ptr)    ( _obj2inst(ptr, struct jsobj_double)->value )
#define _obj2string(ptr)    ( _obj2inst(ptr, struct jsobj_string)->value )

struct jsobj_nil
{
    struct jsobj base;
};

struct jsobj_bool
{
    struct jsobj base;
    bool value;             // 0 or 1
};

struct jsobj_int
{
    struct jsobj base;
    int value;
};

struct jsobj_double
{
    struct jsobj base;
    double value;
};

struct jsobj_string
{
    struct jsobj base;
    char* value;
};

struct jsobj_array
{
    struct jsobj base;
    unsigned int capacity, size;
    struct jsobj** values;
};

struct map_pair
{
    struct jsobj *key;
    struct jsobj *value;
};

struct jsobj_map
{
    struct jsobj base;
    unsigned int capacity, size;
    struct map_pair* pairs;
};

struct jsobj* cjson_clone(struct jsobj* obj);
int cjson_compare(struct jsobj* obj0, struct jsobj* obj1);
struct jsobj* cjson_make(enum jstype type);
enum jserr cjson_release(struct jsobj* obj);

struct jsobj* cjson_make_nil(void);
struct jsobj* cjson_clone_nil(struct jsobj_nil* nil_obj);
enum jserr cjson_release_nil(struct jsobj_nil* nil_obj);

struct jsobj* cjson_make_bool(bool value);
struct jsobj* cjson_clone_bool(struct jsobj_bool* bool_obj);
enum jserr cjson_release_bool(struct jsobj_bool* bool_obj);

struct jsobj* cjson_make_int(int value);
struct jsobj* cjson_clone_int(struct jsobj_int* int_obj);
enum jserr cjson_release_int(struct jsobj_int* int_obj);

struct jsobj* cjson_make_double(double value);
struct jsobj* cjson_clone_double(struct jsobj_double* double_obj);
enum jserr cjson_release_double(struct jsobj_double* double_obj);

struct jsobj* cjson_make_string(char *value);
struct jsobj* cjson_clone_string(struct jsobj_string* string_obj);
enum jserr cjson_release_string(struct jsobj_string* string_obj);

struct jsobj* cjson_make_array(void);
struct jsobj* cjson_clone_array(struct jsobj_array* array_obj);
int cjson_compare_array(struct jsobj_array* array0, struct jsobj_array* array1);
enum jserr cjson_array_add_object(struct jsobj* array, struct jsobj* value);
enum jserr cjson_release_array(struct jsobj_array* array_obj);

struct jsobj* cjson_make_map(void);
struct jsobj* cjson_clone_map(struct jsobj_map* map_obj);
int cjson_compare_map(struct jsobj_map* map0, struct jsobj_map* map1);      // if two map have the same keys in all pairs, they are the same.
enum jserr cjson_map_add_object(struct jsobj* map, struct jsobj* key, struct jsobj* value);
enum jserr cjson_release_map(struct jsobj_map* map_obj);


// debug
void cjson_memory_profile(void** alloc_record, unsigned int* alloc_idx, void** release_record, unsigned int* release_idx, unsigned int size);



#endif //DYBUF_C_CJSON_H
