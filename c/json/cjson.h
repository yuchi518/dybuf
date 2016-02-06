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
 *      foreach (key, value in dictionary)
 *      {
 *          //
 *      }
 *
 *      foreach(dictionary, lambda(key, value) {});
 *  }
 *  sayhi("cyc", 1)
 */

enum jtype
{
    jtyp_nil            = 0x000,

    jtyp_int            = 0x010,        // 32bits or 64bits, depends on platform

    jtyp_double         = 0x020,

    jtyp_string         = 0x030,

    jtyp_object         = 0x100,
    jtyp_array,
    jtyp_dictionary,

    jtyp_closure        = 0x200,
    jtyp_express,                       // expr is a closure with a return boolean value
    jtyp_lambda,                        // lambda is a functional closure with input/output values

};

struct j_object
{
    enum jtype type;
    unsigned int should_copy:1;
    unsigned int reference_count;
    struct j_object* wrapper;           // belong to which closure
    struct j_object* this;
    void* instant[0];
};


#endif //DYBUF_C_CJSON_H
