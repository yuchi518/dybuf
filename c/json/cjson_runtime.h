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

#ifndef DYBUF_C_CJSON_RUNTIME_H
#define DYBUF_C_CJSON_RUNTIME_H

#include "cjson.h"


void cjson_rt_push_new_runtime(void);
struct jsobj* cjson_rt_pop_last_runtime(void);
struct jsobj* cjson_rt_add_code_segment(struct jsobj* segment);

enum jserr cjson_source_push_from_buffer(const char* buf, uint size);
enum jserr cjson_source_push_from_resource(const char* resource_name);
enum jserr cjson_source_pop(void);
enum jserr cjson_source_analyze(void);

#endif //DYBUF_C_CJSON_RUNTIME_H
