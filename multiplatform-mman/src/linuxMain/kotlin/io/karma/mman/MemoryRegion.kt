package io.karma.mman

import platform.posix.getpagesize

/**
 * @author Alexander Hinze
 * @since 30/10/2024
 */

actual val PAGE_SIZE: Long
    get() = getpagesize().toLong()