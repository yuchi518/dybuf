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
// Created by Yuchi on 2016/3/17.
//

#ifndef DYBUF_C_DYPKT_H
#define DYBUF_C_DYPKT_H

#include "dybuf.h"

typedef struct dybuf dypkt;

enum dype
{
    dype_eof            = typdex_typ_eof,
    dype_bool           = typdex_typ_bool,                  // 1 byte boolean
    dype_int            = typdex_typ_int,                   // variable size int64
    dype_uint           = typdex_typ_uint,                  // variable size uint64
#if !defined(__KERNEL__) && !defined(DISABLE_FP)
    dype_float          = typdex_typ_float,                 // 4 bytes float
    dype_double         = typdex_typ_double,                // 8 bytes double
#endif    
    dype_string         = typdex_typ_string,                // variable length string (include '\0')
    dype_bytes          = typdex_typ_bytes,                 // variable length binary
    //dype_array        = typdex_typ_array,                 // array of items
    //dype_map          = typdex_typ_map,                   // items map
    dype_version        = typdex_typ_version,               // version
};
typedef enum dype dype;


/**
 *  
 */
dyb_inline dypkt* dyp_pack(dypkt* dyp, byte* data, uint capacity)
{
    if (data) return dyb_refer(dyp, data, capacity, true);
    else return dyb_create(dyp, capacity);
}

/**
 *  
 */
dyb_inline dypkt* dyp_unpack(dypkt* dyp, byte* data, uint len, boolean clone)
{
    if (data)
    {
        if (clone) return dyb_copy(dyp, data, len, true);
        else return dyb_refer(dyp, data, len, false);
    }
    return null;
}

dyb_inline void dyp_release(dypkt* dyp)
{
    dyb_release(dyp);
}

/// =====

dyb_inline uint dyp_get_position(dypkt* dyp)
{
    return dyb_get_position(dyp);
}

dyb_inline uint dyp_get_remainder(dypkt* dyp)
{
    return dyb_get_remainder(dyp);
}

/// ===== pack functions =====

dyb_inline dypkt* dyp_append_version(dypkt* dyp, uint32 version)
{
    dyb_append_typdex(dyp, dype_version, version);
    return dyp;
}

dyb_inline dypkt* dyp_append_eof(dypkt* dyp)
{
    dyb_append_typdex(dyp, dype_eof, 0);
    return dyp;
}

dyb_inline dypkt* dyp_append_bool(dypkt* dyp, uint32 index, boolean value)
{
    dyb_append_typdex(dyp, dype_bool, index);
    dyb_append_bool(dyp, value);
    return dyp;
}

dyb_inline dypkt* dyp_append_int(dypkt* dyp, uint32 index, int64 value)
{
    dyb_append_typdex(dyp, dype_int, index);
    dyb_append_var_s64(dyp, value);
    return dyp;
}

dyb_inline dypkt* dyp_append_uint(dypkt* dyp, uint32 index, uint64 value)
{
    dyb_append_typdex(dyp, dype_uint, index);
    dyb_append_var_u64(dyp, value);
    return dyp;
}

#if !defined(__KERNEL__) && !defined(DISABLE_FP)

dyb_inline dypkt* dyp_append_float(dypkt* dyp, uint32 index, float value)
{
    dyb_append_typdex(dyp, dype_float, index);
    dyb_append_float(dyp, value);
    return dyp;
}

dyb_inline dypkt* dyp_append_double(dypkt* dyp, uint32 index, double value)
{
    dyb_append_typdex(dyp, dype_double, index);
    dyb_append_double(dyp, value);
    return dyp;
}

#endif

dyb_inline dypkt* dyp_append_cstring(dypkt* dyp, uint32 index, char* string, uint size)
{
    dyb_append_typdex(dyp, dype_string, index);
    dyb_append_cstring_with_var_len(dyp, string, size);
    return dyp;
}

dyb_inline dypkt* dyp_append_data(dypkt* dyp, uint32 index, uint8* data, uint size)
{
    dyb_append_typdex(dyp, dype_bytes, index);
    dyb_append_data_with_var_len(dyp, data, size);
    return dyp;
}



/// ===== next functions =====
dyb_inline dype dyp_next_type(dypkt* dyp, uint32 *index)
{
    uint8 typ;
    uint32 idx;

    dyb_peek_typdex(dyp, &typ, &idx);
    if (index) *index = idx;
    return (dype)typ;
}

dyb_inline uint32 dyp_next_version(dypkt* dyp)
{
    uint32 idx;
    dyb_next_typdex(dyp, null, &idx);
    return idx;
}

dyb_inline void dyp_next_eof(dypkt* dyp)
{
    dyb_next_typdex(dyp, null, null);
}

dyb_inline boolean dyp_next_bool(dypkt* dyp)
{
    dyb_next_typdex(dyp, null, null);
    return dyb_next_bool(dyp);
}

dyb_inline int64 dyp_next_int(dypkt* dyp)
{
    dyb_next_typdex(dyp, null, null);
    return dyb_next_var_s64(dyp);
}

dyb_inline uint64 dyp_next_uint(dypkt* dyp)
{
    dyb_next_typdex(dyp, null, null);
    return dyb_next_var_u64(dyp);
}

#if !defined(__KERNEL__) && !defined(DISABLE_FP)

dyb_inline float dyp_next_int(dypkt* dyp)
{
    dyb_next_typdex(dyp, null, null);
    return dyb_next_float(dyp);
}

dyb_inline double dyp_next_int(dypkt* dyp)
{
    dyb_next_typdex(dyp, null, null);
    return dyb_next_double(dyp);
}

#endif

dyb_inline char* dyp_next_cstring(dypkt* dyp, uint* size)
{
    dyb_next_typdex(dyp, null, null);
    return dyb_next_cstring_with_var_len(dyp, size);
}

dyb_inline uint8* dyp_next_data(dypkt* dyp, uint* size)
{
    dyb_next_typdex(dyp, null, null);
    return dyb_next_data_with_var_len(dyp, size);
}


#endif

