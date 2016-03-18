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
// Created by Yuchi on 2015/12/19.
//

#include <time.h>
#include <stdio.h>
#include <stdlib.h>
#include <cjson_runtime.h>
#include "dybuf.h"
#include "dypkt.h"
#include "cjson.h"

void cjson_test(void);
void cjson_parse_test(void);
void dybuf_test(void);
void dybuf_test_ref(void);
void dypkt_test(void);

int main(int argc, char **argv)
{
    srand(time(NULL));

    unsigned int profile_size = 1024, alloc_idx, release_idx, i;
    void** alloc_rec = malloc(profile_size*sizeof(alloc_rec[0]));
    void** release_rec = malloc(profile_size*sizeof(release_rec[0]));
    cjson_memory_profile(alloc_rec, &alloc_idx, release_rec, &release_idx, profile_size);

    cjson_test();
    cjson_parse_test();

    printf("alloc: ");
    for (i=0; i<alloc_idx; i++)
    {
        printf("%p,", alloc_rec[i]);
    }
    printf("\nrelease: ");
    for (i=0; i<release_idx; i++)
    {
        printf("%p,", release_rec[i]);
    }
    printf("\n");


    dybuf_test();
    dybuf_test_ref();
    dypkt_test();

    return 0;
}

void cjson_test(void)
{
    struct jsobj *obj;
    enum jstype typs[] = {jstype_nil, jstype_int, jstype_double, jstype_string, jstype_array, jstype_tuple, jstype_map};
    unsigned int i;

    for (i=0; i<sizeof(typs)/sizeof(typs[0]); i++)
    {
        obj = cjson_make(typs[i]);
        cjson_release(obj);
    }
}

void cjson_parse_test(void)
{
    const char* json_text = "{\"abc\":1,\"efg\":[], 1:[1,2.2,3e3,4,0xa]}\n";
    //const char* json_text = "[\n1]\n\0\0";
    //const char* json_text = "[1,2.2,3e3,4,0xa,\"asdfs\"]\n";
    //const char* json_text = "{1:2}\n";
    cjson_rt_push_new_runtime();

    //cjson_source_push_from_resource("");
    if (cjson_source_push_from_buffer(json_text, strlen(json_text)+1) == jserr_no_error)
        cjson_source_analyze();

    struct jsobj* rt = cjson_rt_pop_last_runtime();
    cjson_release(rt);
}

void dybuf_test(void)
{
    dybuf dyb0, *dyb1;
    int i=0;
    int64 v0, v1;
    double d0, d1;
    uint size;
    int diff = 0;

    for (i=0; i<1000; i++)
    {
        dyb_create(&dyb0, 32);

        v0 = rand();
        v0 *= rand();
        d0 = (double)v0/rand()/rand();

        dyb_append_var_s64(&dyb0, v0);
        dyb_append_double(&dyb0, d0);

        uint8* data = dyb_get_data_before_current_position(&dyb0, &size);

        dyb1 = dyb_copy(null, data, size, false);

        v1 = dyb_next_var_s64(dyb1);
        d1 = dyb_next_double(dyb1);

        if (v0 == v1)
            ;//printf("v0(%lld) == v1(%lld)\n", v0, v1);
        else {
            printf("v0(%lld) != v1(%lld)\n", v0, v1);
            diff++;
        }

        if (d0 == d1)
            ;//printf("d0(%f) == d1(%f)\n", d0, d1);
        else{
            printf("d0(%f) != d1(%f)\n", d0, d1);
            diff++;
        }

        dyb_release(&dyb0);
        dyb_release(dyb1);
    }

    printf("diff: %d\n", diff);
}

void dybuf_test_ref(void)
{
    dybuf dyb0, dyb1;
    unsigned char data[32];
    int i=0;
    int64 v0, v1;
    double d0, d1;
    //uint size;
    int diff = 0;

    for (i=0; i<1000; i++)
    {
        dyb_refer(&dyb0, data, 32, true);

        v0 = rand();
        v0 *= rand();
        d0 = (double)v0/rand()/rand();

        dyb_append_var_s64(&dyb0, v0);
        dyb_append_double(&dyb0, d0);

        //uint8_t* data = dyb_get_data_before_current_position(&dyb0, &size);
        dyb_release(&dyb0);

        dyb_refer(&dyb1, data, 32, true);

        v1 = dyb_next_var_s64(&dyb1);
        d1 = dyb_next_double(&dyb1);

        if (v0 == v1)
            ;//printf("v0(%lld) == v1(%lld)\n", v0, v1);
        else {
            printf("v0(%lld) != v1(%lld)\n", v0, v1);
            diff++;
        }

        if (d0 == d1)
            ;//printf("d0(%f) == d1(%f)\n", d0, d1);
        else{
            printf("d0(%f) != d1(%f)\n", d0, d1);
            diff++;
        }

        dyb_release(&dyb1);
    }

    printf("diff: %d\n", diff);
}

void dypkt_test(void)
{
    uint8 mem[1024];
    dypkt* dyp0 = dyp_pack(null, mem, 1024);
    dypkt* dyp1;
    uint index = 0;
    uint size;
    dype type;
    dyp_append_version(dyp0, 0x01020304u);
    dyp_append_protocol(dyp0, "json");
    dyp_append_bool(dyp0, index++, true);
    dyp_append_int(dyp0, index++, (int64)-1ll);
    dyp_append_uint(dyp0, index++, (uint64)-1ll);
    dyp_append_cstring(dyp0, index++, "I love dybuf lib!!");
    dyp_append_float(dyp0, index++, 0.1);
    dyp_append_double(dyp0, index++, 0.2);
    dyp_append_eof(dyp0);

    size = dyp_get_position(dyp0);
    dyp_release(dyp0);

    dyp1 = dyp_unpack(null, mem, size, false);

    while(dyp_get_remainder(dyp1)>0)
    {
        type = dyp_next_type(dyp1, &index);
        if (type==dype_f) printf("_f.");
        else printf("%2d.", index);
        switch (type)
        {
            case dype_f:
            {
                switch((dype_fid)index)
                {
                    case dype_f_eof:
                    {
                        dyp_next_eof(dyp1);
                        printf("eof\n");
                        break;
                    }
                    case dype_f_version:
                    {
                        printf("version: %llx\n", dyp_next_version(dyp1));
                        break;
                    }
                    case dype_f_protocol:
                    {
                        printf("protocol: %s\n", dyp_next_protocol(dyp1, null));
                        break;
                    }
                }
                break;
            }
            case dype_bool:
            {
                printf("boolean: %s\n", dyp_next_bool(dyp1)?"true":"false");
                break;
            }
            case dype_int:
            {
                printf("int: %llx\n", dyp_next_int(dyp1));
                break;
            }
            case dype_uint:
            {
                printf("uint: %llx\n", dyp_next_uint(dyp1));
                break;
            }
            case dype_float:
            {
                printf("float: %f\n", dyp_next_float(dyp1));
                break;
            }
            case dype_double:
            {
                printf("double: %f\n", dyp_next_double(dyp1));
                break;
            }
            case dype_string:
            {
                printf("cstr: %s\n", dyp_next_cstring(dyp1, null));
                break;
            }
            case dype_bytes:
            {
                // todo
                break;
            }
        }
    }

    printf("remainder: %u\n", dyp_get_remainder(dyp1));
    printf("size: %u ? %u\n", size, dyp_get_position(dyp1));

    dyp_release(dyp1);
}