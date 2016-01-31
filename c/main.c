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
#include "dybuf.h"

int main(int argc, char **argv)
{
    dybuf dyb0, *dyb1;
    int i=0;
    int64_t v0, v1;
    double d0, d1;
    uint size;
    int diff = 0;

    srand(time(NULL));

    for (i=0; i<1000000; i++)
    {
        dyb_create(&dyb0, 32);

        v0 = rand();
        v0 *= rand();
        d0 = (double)v0/rand()/rand();

        dyb_append_var_s64(&dyb0, v0);
        dyb_append_double(&dyb0, d0);

        uint8_t* data = dyb_get_data_before_current_position(&dyb0, &size);

        dyb1 = dyb_copy(null, data, size, false);

        v1 = dyb_next_var_s64(dyb1);
        d1 = dyb_next_double(dyb1);

        if (v0 == v1)
            printf("v0(%lld) == v1(%lld)\n", v0, v1);
        else {
            printf("v0(%lld) != v1(%lld)\n", v0, v1);
            diff++;
        }

        if (d0 == d1)
            printf("d0(%f) == d1(%f)\n", d0, d1);
        else{
            printf("d0(%f) != d1(%f)\n", d0, d1);
            diff++;
        }

        dyb_release(&dyb0);
        dyb_release(dyb1);
    }

    printf("diff: %d\n", diff);


    return 0;
}
