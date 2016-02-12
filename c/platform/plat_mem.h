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

#ifdef __KERNEL__
// add header
#else
#include <stdlib.h>
#include <string.h>
#endif


plat_inline void* plat_mem_allocate(uint size)
{
#ifdef __KERNEL__
    return null;
#else
    return malloc(size);
#endif
}


plat_inline void plat_mem_release(void* mem)
{
#ifdef __KERNEL__
#else
    free(mem);
#endif
}


plat_inline void plat_mem_copy(void* dest, void* src, uint size)
{
#ifdef __KERNEL__
#else
    memcpy(dest, src, size);
#endif
}


plat_inline void plat_mem_move(void* dest, void* src, uint size)
{
#ifdef __KERNEL__
#else
    memmove(dest, src, size);
#endif
}

#endif //_PLAT_C_MEM_


