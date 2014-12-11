# Process this file with autoconf to produce a configure script.

AC_PREREQ([2.65])
AC_INIT
AC_CONFIG_SRCDIR([include/Monitors.h])
AC_CONFIG_HEADER([include/Config.h])
AC_CANONICAL_SYSTEM()

# Checks for programs.
AC_PROG_CC(xlc pgi icc gcc cc)
AC_PROG_INSTALL

# Determine platform specific settings
AC_CONFIG_MACRO_DIR([m4])
m4_include([m4/acx_mpi.m4])
AC_LANG([C])
ACX_MPI

#echo "$target_os"
#echo "$target_cpu"

# performing this replacement means that all subseqtent compiler tests use MPICC, which is really what we want.
CC="$MPICC"

AC_MSG_CHECKING(architecture)
ARCH="`uname -m`"
AC_MSG_RESULT($ARCH)

AC_MSG_CHECKING(operating system)
OS="`uname | tr '[A-Z]' '[a-z]'`"
AC_MSG_RESULT($OS)

DBG="-g -w"
OPT="-O3"
AR="ar"
CFLAGS="$DBG $OPT"
LRGFILEFLGS="-D_LARGEFILE64_SOURCE -D_FILE_OFFSET_BITS=64"

if test "$EXTRA_CFLGS" = ""; then
   EXTRA_CFLGS="-DMPI_MEASURE -DMPI_TRACE"
fi

AC_ARG_WITH([platform_bits],
             [AS_HELP_STRING([--with-platform-bits=<32|64>],[specify 32/64 bit architecture (default=64)])],
             [platform_bits=$withval
              if test "$platform_bits" = "32"; then
                 PLATFORMBITS=32
              elif test "$platform_bits" = "64"; then
                 PLATFORMBITS=64
              else
                 AC_MSG_ERROR(Invalid option: --with-platform-bits=<32|64>)
              fi],
             [PLATFORMBITS=64])
if test "$PLATFORMBITS" = "64"; then
   AC_MSG_NOTICE(Using platform size... 64-bit)
   CFLAGS="$CFLAGS $LRGFILEFLGS"
else
   AC_MSG_NOTICE(Using platform size... 32-bit)   
fi  
AC_SUBST(PLATFORMBITS)


AC_CHECK_FILE([/proc/cray_xt],[cray_xt=yes])

if test "$OS" = aix; then
   AC_MSG_NOTICE(Setting compiler flags for... AIX)
   AIX_FLAGS="-qinline -qstrict"
#   CFLAGS="$CFLAGS $AIX_FLAGS -DAIX -DFORT_ADD_UNDERSCORE"
   CFLAGS="$CFLAGS $AIX_FLAGS -DAIX"
   if test "$PLATFORMBITS" = "64"; then
      AC_MSG_NOTICE(Setting 64-bit AIX flags)
      CFLAGS="$CFLAGS -q64"
      ARFLAGS=-X64
   else
      AC_MSG_NOTICE(Setting 32-bit AIX flags)
   fi
elif test "$OS" = CLE -o "$cray_xt" = yes; then
   AC_MSG_NOTICE(Setting compiler flags for... Cray XT)
   MPICC=cc
elif test "$OS" = linux; then
   AC_MSG_NOTICE(Setting compiler flags for... Linux)
else
   AC_MSG_ERROR(This platform is not supported)
fi

if test "$arch" = ppc64; then
   CFLAGS="$CFLAGS -DBIG_ENDIAN_LAYOUT"
fi

if test "$OS" = aix; then
   EXTRA_PAPI_LIB="-lpmapi"
else
   EXTRA_PAPI_LIB="-lpfm"
fi

AC_ARG_WITH(papi_headers,
	    [AS_HELP_STRING([--with-papi-headers=<path>],[specify path to PAPI headers])],
	    [papi_headers=$withval
             AC_CHECK_HEADER([$papi_headers/papi.h],
                             [CFLAGS="$CFLAGS -I$papi_headers"],
                             [AC_MSG_ERROR(papi.h not found using $papi_headers)])],
            [papi_headers=no])

AC_ARG_WITH(papi_lib,
            [AS_HELP_STRING([--with-papi-lib=<path>],[specify path to PAPI lib])],
	    [papi_lib=$withval
             EXTRA_LIBS="$EXTRA_LIBS -L$papi_lib -lpapi $EXTRA_PAPI_LIB"],
	     AC_CHECK_LIB([papi],[PAPI_shutdown],
                          [EXTRA_LIBS="$EXTRA_LIBS -lpapi $EXTRA_PAPI_LIB"],
                          [papi_lib=no])
            [papi_lib=no])

AC_ARG_ENABLE(papi,
              [AS_HELP_STRING([--enable-papi=no])],
              [if test "$enable_papi" != no; then
                  AC_MSG_ERROR("Invalid argument --enable-papi=$enable_papi")
               fi
               papi=no],
              [])

if test "$papi_headers" = no -o "$papi_lib" = no; then
   if test "$papi" = no; then
      AC_MSG_NOTICE(Papi disabled manually)
      CFLAGS="$CFLAGS -DUSE_NOHWC"
   else
      AC_MSG_ERROR("cannot find papi (try configuring with --disable-papi)")
   fi
fi

USE_SHARED="no"
AC_ARG_ENABLE(shared,
        [AS_HELP_STRING([--enable-shared=yes <default=no>])],
        [if test "$enable_shared" == yes; then
            USE_SHARED="yes"
         fi],
        [])           
AC_SUBST(USE_SHARED)

AC_ARG_WITH([timing_method],
            [AS_HELP_STRING([--with-timing-method=<clock_gettime,gettimeofday,mpi_wtime,papi,platform_highres>],[specify the timing method to use])],
            [timing_method=$withval
             if test "$papi" = no -a "$timing_method" = papi; then
                AC_MSG_ERROR(PAPI is not part of the build and cannot be used for timing)
             fi],
            [timing_method=gettimeofday])

if test "$timing_method" = platform_highres; then
   AC_MSG_NOTICE(Timing method is platform dependent code)
   CFLAGS="$CFLAGS -DUSE_TIMER_PLATFORM_HIGHRES"
elif test "$timing_method" = clock_gettime; then
   CFLAGS="$CFLAGS -DUSE_TIMER_CLOCK_GETTIME"
   AC_COMPILE_IFELSE([AC_LANG_PROGRAM([[#include <time.h>]],
                     [[clock_gettime(CLOCK_MONOTONIC,(struct timespec*)0)]])],                                
                     [have_clock_gettime=yes])
   if test "$have_clock_gettime" != yes; then
      AC_MSG_ERROR(clock_gettime is unavailable);
   fi
elif test "$timing_method" = gettimeofday; then
   AC_MSG_NOTICE(Timing method is gettimeofday) 
   CFLAGS="$CFLAGS -DUSE_TIMER_GETTIMEOFDAY"
elif test "$timing_method" = mpi_wtime; then
   AC_MSG_NOTICE(Timing method is MPI_Wtime)
   CFLAGS="$CFLAGS -DUSE_TIMER_MPI_WTIME"
elif test "$timing_method" = papi; then
   AC_MSG_NOTICE(Timing method is PAPI_get_real_usec)
   CFLAGS="$CFLAGS -DUSE_TIMER_PAPI_GET_REAL_USEC"
else
   AC_MSG_ERROR(Timing method $timing_method is not valid)
fi

# check if we need to use an underscore when building C functions that get called from fortran
AC_FC_FUNC(timer, timer_name)
if test "x$timer_name" = xtimer_; then
   CFLAGS="$CFLAGS -DFORT_ADD_UNDERSCORE"
elif test "x$timer_name" = xtimer; then
  somenonemptystatement=1
else
  AC_MSG_WARN(fortran name mangling scheme is unexpected)
fi

AC_SUBST(AR)
AC_SUBST(ARFLAGS)
AC_SUBST(CFLAGS)
AC_SUBST(EXTRA_CFLGS)
AC_SUBST(EXTRA_LIBS)
AC_SUBST(SERIAL_CC)

# Checks for libraries.

# Checks for header files.
AC_HEADER_TIME
AC_CHECK_HEADERS([stdint.h stdlib.h string.h strings.h sys/time.h c_asm.h intrinsics.h mach/mach_time.h unistd.h])

# Checks for typedefs, structures, and compiler characteristics.
AC_C_INLINE
if test "$ac_cv_c_inline" = "no"; then
   INLINE_DEF=""
else
   INLINE_DEF="$ac_cv_c_inline"
fi
CFLAGS="$CFLAGS -Dinline=$INLINE_DEF"


AC_TYPE_INT32_T
AC_TYPE_INT64_T
AC_TYPE_SIZE_T
AC_TYPE_UINT16_T
AC_TYPE_UINT32_T
AC_TYPE_UINT64_T
AC_TYPE_UINT8_T
AC_CHECK_TYPE([hrtime_t],
              [AC_DEFINE(HAVE_HRTIME_T, 1, [Define to 1 if hrtime_t is defined in <sys/time.h>])],,
              [#if HAVE_SYS_TIME_H
               #include <sys/time.h>
               #endif])

# Checks for library functions.
AC_FUNC_MALLOC
AC_CHECK_FUNCS([bzero getpagesize memset strchr gethrtime read_real_time time_base_to_time clock_gettime mach_absolute_time])
	    
if test "$OS" = "CLE"; then
   AC_MSG_CHECKING([for _rtc intrinsic])
   rtc_ok=yes
   AC_TRY_LINK([#ifdef HAVE_INTRINSICS_H
                #include <intrinsics.h>
                #endif], 
               [_rtc()], 
               [AC_DEFINE(HAVE__RTC,1,[Define for _rtc() intrinsic])], 
               [rtc_ok=no
                AC_DEFINE(NO_RTC_INTRINSIC,1,[Define if _rtc() is not found])])
   AC_MSG_RESULT($rtc_ok)
fi

AC_CONFIG_FILES([Makefile
                 convert/Makefile
                 tests/Makefile
                 src/Makefile])
AC_OUTPUT
