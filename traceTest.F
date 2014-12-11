* Copyright (c) 2010, The Regents of the University of California
* All rights reserved.

* Redistribution and use in source and binary forms, with or without modification, are
* permitted provided that the following conditions are met:

*  Redistributions of source code must retain the above copyright notice, this list of conditions
*   and the following disclaimer.
*  Redistributions in binary form must reproduce the above copyright notice, this list of conditions
*   and the following disclaimer in the documentation and/or other materials provided with the distribution.
*  Neither the name of the Regents of the University of California nor the names of its contributors may be
*   used to endorse or promote products derived from this software without specific prior written permission.

* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
* WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
* PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
* ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
* LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
* INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
* TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
* ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

      program main 
         include 'mpif.h'
         integer rank, size
         logical success, testMPI, verifyFiles
         call MPI_INIT(ierr)
         call MPI_COMM_RANK(MPI_COMM_WORLD, rank, ierr)
         call MPI_COMM_SIZE(MPI_COMM_WORLD, size, ierr)
         success = testMPI(rank, size)
         call MPI_FINALIZE(ierr)
         if (rank.eq.1) then
            if (success) then
               success = verifyFiles(size)
            endif
            call cleanup()
            if (success) then
               write(*,*) 'PASS'
               call exit(0)
            else
               write(*,*) 'FAIL'
               call exit(1)
            endif
         endif
      end

      logical function verifyFiles(taskCount)
         logical result
         integer taskCount, i
         character*128 fileName 
         character*128 psinsFile 
         character*128 command
         inquire(file='RankPid', exist=result)
         if (.not.result) then
            go to 99          
         endif
         inquire(file='RankPid.extended', exist=result)
         if (.not.result) then
            go to 99          
         endif
         do i = 0, taskCount - 1
            write(fileName,10) i, taskCount
 10         format('psinstrace.rank',I5.5,'.tasks',I5.5,'.bin')
            inquire(file=fileName, exist=result)
            if (.not.result) then
               go to 99          
            endif           
         end do
         write(command,20) taskCount
 20      format('./bin/mpi2psins --application traceTest',
     +          ' --dataset standard --cpu_count ',I4,' --trace_dir .',
     +          ' --ease_requests --dyn_constants')
         call system(command)
         write(psinsFile,30) taskCount
 30      format('traceTest_standard_',I4.4,'.psins')
         inquire(file=psinsFile, exist=result)
         if (.not.result) then
            go to 99          
         endif        
 99      verifyFiles = result
      end

      subroutine cleanup()
         open(unit=1, file='RankPid')  
         close(unit=1, status='delete')
         open(unit=2, file='RankPid.extended') 
         close(unit=2, status='delete')
         call system('rm *.bin')
         call system('rm *.psins')
      end

