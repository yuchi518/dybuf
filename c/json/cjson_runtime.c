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
// Created by Yuchi on 2016/2/12.
//

#include <plat_mem.h>
#include "cjson_runtime.h"

/// ==== json yacc & lex
#include "json.lex.c"


static struct jsobj* rt_list[8];
static uint rt_index = 0;

void cjson_rt_push_new_runtime(void)
{
    struct jsobj* rt;

    rt = cjson_make_runtime();

    rt_list[rt_index++] = rt;
}

struct jsobj* cjson_rt_pop_last_runtime(void)
{
    struct jsobj* rt;

    rt = rt_list[rt_index--];

    return rt;
}


enum jserr cjson_source_push_from_buffer(const char* buff, uint size)
{
    struct src_stack *bs = plat_mem_allocate(sizeof(*bs));
    if(!bs) return jserr_no_memory;

    bs->src_buffer = plat_mem_allocate(size);
    if(!bs->src_buffer)
    {
        plat_mem_release(bs);
        return jserr_no_memory;
    }

    bs->src_size = size;
    plat_mem_copy(bs->src_buffer, buff, size);

    /* remember state */
    if(curbs) curbs->lineno = yylineno;
    bs->prev = curbs;

    /* set up current entry */
    bs->bs = yy_scan_bytes(bs->src_buffer, bs->src_size);
    yy_switch_to_buffer(bs->bs);
    curbs = bs;
    yylineno = 1;

    return jserr_no_error;
}

enum jserr cjson_source_push_from_resource(const char* resource_name)
{
    unsigned int size;
    char *buf;

    int r = plat_io_get_resource(resource_name, (void**)&buf, &size);

    if (r) return jserr_no_source;
    if (!size) return jserr_no_source;  // no content

    enum jserr jsr = cjson_source_push_from_buffer(buf, size);
    free(buf);

    return jsr;
}

enum jserr cjson_source_pop(void)
{
    struct src_stack *bs = curbs;
    struct src_stack *prevbs;

    if(!bs) return jserr_no_source;

    /* get rid of current entry */
    plat_mem_release(bs->src_buffer);
    yy_delete_buffer(bs->bs);

    /* switch back to previous */
    prevbs = bs->prev;
    plat_mem_release(bs);

    if(!prevbs) return jserr_no_source;

    yy_switch_to_buffer(prevbs->bs);
    curbs = prevbs;
    yylineno = curbs->lineno;

    return jserr_no_error;
}

enum jserr cjson_source_analyze(void)
{
    yyparse();
    yylex();
    return jserr_no_error;
}




