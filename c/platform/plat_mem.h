/*
 * plat_c, platform independent library for c
 * Copyright (C) 2016 Yuchi (yuchi518@gmail.com)

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

#ifndef _PLAT_C_MEM_
#define _PLAT_C_MEM_

#include "plat_type.h"

#if !_NO_STD_INC_
#ifdef __KERNEL__
// add header
#else
#include <stdlib.h>
#include <string.h>
#endif
#endif

plat_inline void* plat_mem_allocate(uint size)
{
#if _NO_STD_INC_
    return NULL;
#else
#ifdef __KERNEL__
    return NULL;
#else
    return malloc(size);
#endif
#endif
}


plat_inline void plat_mem_release(void* mem)
{
#if !_NO_STD_INC_
#ifdef __KERNEL__
#else
    free(mem);
#endif
#endif
}


plat_inline void plat_mem_copy(void* dest, void* src, uint size)
{
#if !_NO_STD_INC_
#ifdef __KERNEL__
#else
    memcpy(dest, src, size);
#endif
#endif
}


plat_inline void plat_mem_move(void* dest, void* src, uint size)
{
#if !_NO_STD_INC_
#ifdef __KERNEL__
#else
    memmove(dest, src, size);
#endif
#endif
}

#endif //_PLAT_C_MEM_


