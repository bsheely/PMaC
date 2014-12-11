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
 
      integer function summation(num)
         integer num, i, temp
         temp = 0
         do i = 1, num 
            temp = temp + i
         end do
         summation = temp
      end

c After calling testAlltoallv, each processor has an array whose values have the pattern [a,1b,1c,2b,2c,2d,3b,3c,3d,3e,...]
c The following 2 functions are used to compute the expected value for any given index so the results can be verified 
c Rank 0 = [0,100,101,200,201,202,300,301,302,303]
c Rank 1 = [1,102,103,203,204,205,304,305,306,307]
c Rank 2 = [2,104,105,206,207,208,308,309,310,311]
 
      real function computeBase(index)
         integer index, i, count, counter
         real a(index), value
         count = 0
         counter = 0
         value = 0
         do i = 1, index
            a(index) = value
            if (count.eq.counter) then
               counter = counter + 1
               count = 0
               value = value + 1
            else
               count = count + 1
            endif            
         enddo
         computeBase = a(index)
      end

      real function computeValue(rank, index)
         integer rank, index, i, count, counter
         real a(index), value
         count = 0
         counter = 0
         value = rank
         do i = 1, index
            a(index) = value
            if (count.eq.counter) then
               counter = counter + 1
               count = 0
               value = rank * (counter + 1)
            else
               count = count + 1
               value = value + 1
            endif            
         enddo
         computeValue = a(index)
      end

********* MPICollect ***********

      logical function testBarrier(rank)
         include 'mpif.h'
         integer rank, tag
         double precision p1, p2
         integer status(MPI_STATUS_SIZE)
         if (rank.eq.1) then
            call sleep(1)
         endif
         call MPI_BARRIER(MPI_COMM_WORLD, ierr);
         tag = 10
         if (rank.eq.0) then 
            p1 = MPI_WTIME();
            call MPI_SEND(p1, 1, MPI_DOUBLE_PRECISION, 1, tag, 
     +                    MPI_COMM_WORLD, ierr)
         else if (rank.eq.1) then
            p2 = MPI_WTIME();  
            call MPI_RECV(p1, 1, MPI_DOUBLE_PRECISION, 0, tag, 
     +                    MPI_COMM_WORLD, status, ierr); 
            if (ABS(p1 - p2).ge.0.25) then                       
               testBarrier = .false.
               return
            endif
         endif            
         testBarrier = .true.
      end

      logical function testBcast(rank)
         include 'mpif.h'
         integer rank
         character msg*64, buffer*64
         msg = 'BCAST 64 byte message from process 0 to all other'//
     +         ' processes'
         if (rank.eq.0) then 
            call MPI_BCAST(msg, 64, MPI_BYTE, 0, MPI_COMM_WORLD, 
     +                     ierr)
         else
            call MPI_BCAST(buffer, 64, MPI_BYTE, 0, MPI_COMM_WORLD, 
     +                     ierr)
            if (buffer.ne.msg) then           
               testBcast = .false.
               return
            endif
         endif            
         testBcast = .true.
      end

      logical function testReduce(rank, size)
         include 'mpif.h'
         integer rank, size, i
         real send, recv, expected
         send = rank * 10
         recv = 0
         expected = 0
         call MPI_REDUCE(send, recv, 1, MPI_REAL, MPI_SUM, 0, 
     +                   MPI_COMM_WORLD, ierr)
         do i = 0, size - 1
            expected = expected + (i * 10)
         enddo   
         if (rank.eq.0.and.recv.ne.expected) then
            testReduce = .false. 
            return
         endif
         if (rank.ne.0.and.recv.ne.0) then    
            testReduce = .false.
            return
         endif
         testReduce = .true.
      end

      logical function testScan(rank, size)
         include 'mpif.h'
         integer summation
         integer rank, size
         real send, recv
         send = rank
         recv = 0
         call MPI_SCAN(send, recv, 1, MPI_REAL, MPI_SUM, 
     +                  MPI_COMM_WORLD, ierr);
         if (recv.ne.summation(rank)) then
            testScan = .false.
            return
         endif
         testScan = .true.
      end

      logical function testAllreduce(rank, size)
         include 'mpif.h'
         integer rank, size, i
         real send, recv, expected
         send = rank * 10
         expected = 0
         call MPI_AllREDUCE(send, recv, 1, MPI_REAL, MPI_SUM, 
     +                      MPI_COMM_WORLD, ierr)
         do i = 0, size - 1
            expected = expected + i * 10
         enddo
         if (recv.ne.expected) then
            testAllreduce = .false.
            return
         endif
         testAllreduce = .true.
      end

      logical function testGather(rank, size)
         include 'mpif.h'
         integer rank, size, i
         real recvBuff(size), send
         send = rank * 10
         do i = 1, size
            recvBuff(i) = 0
         enddo
         call MPI_GATHER(send, 1, MPI_REAL, recvBuff, 1, MPI_REAL, 0, 
     +                   MPI_COMM_WORLD, ierr)
         do i = 1, size
            if (rank.eq.0.and.recvBuff(i).ne.(i - 1) * 10) then
               testGather = .false.
               return
            endif
            if (rank.ne.0.and.recvBuff(i).ne.0) then
               testGather = .false.
               return
            endif
         enddo
         testGather = .true.
      end

      logical function testScatter(rank, size)
         include 'mpif.h'
         integer rank, size, i
         real sendBuff(size), recv
         if (rank.eq.0) then
            do i = 1, size
               sendBuff(i) = (i - 1) * 10
            enddo
         endif 
         call MPI_SCATTER(sendBuff, 1, MPI_REAL, recv, 1, MPI_REAL, 0, 
     +                    MPI_COMM_WORLD, ierr);
         if (recv.ne.rank * 10) then
            testScatter = .false.
            return
         endif
         testScatter = .true.
      end

      logical function testAllgather(rank, size)
         include 'mpif.h'
         integer rank, size, i
         real recvBuff(size), send
         send = rank * 10
         call MPI_AllGATHER(send, 1, MPI_REAL, recvBuff, 1, MPI_REAL, 
     +                      MPI_COMM_WORLD, ierr)
         do i = 1, size
            if (recvBuff(i).ne.(i - 1) * 10) then
               testAllgather = .false.
               return
            endif
         enddo
         testAllgather = .true.
      end

      logical function testAlltoall(rank, size)
         include 'mpif.h'
         integer rank, size, i
         real sendBuff(size), recvBuff(size)
         do i = 1, size
            sendBuff(i) = rank * 10 + i - 1
         enddo
         call MPI_ALLTOALL(sendBuff, 1, MPI_REAL, recvBuff, 1, MPI_REAL, 
     +                     MPI_COMM_WORLD, ierr);
         do i = 1, size
            if (recvBuff(i).ne.(i - 1) * 10 + rank) then
               testAlltoall = .false.
               return
            endif
         enddo
         testAlltoall = .true.
      end

      logical function testGatherv(rank, size, buffSize)
         include 'mpif.h'
         integer summation
         integer rank, size, buffSize, i
         integer recvCounts(size), displs(size)
         real sendBuff(rank + 1), recvBuff(buffSize), expected, j
         j = 1
         do i = 1, buffSize
            recvBuff(i) = 0
         enddo
         do i = 1, size
            recvCounts(i) = i
            displs(i) = summation(i - 1)
         enddo
         do i = 1, rank + 1
            sendBuff(i) = (10 * (rank + 1)) + i - 1
         enddo
         call MPI_GATHERV(sendBuff, rank + 1, MPI_REAL, recvBuff, 
     +                    recvCounts, displs, MPI_REAL, 0, 
     +                    MPI_COMM_WORLD, ierr);  
         do i = 1, buffSize
            if (MOD(recvBuff(i),10 * j).eq.0) then
               expected = 10 * i
            else
               expected = MOD(recvBuff(i),10 * j)
            endif
            if (rank.eq.0.and.recvBuff(i).ne.expected) then
               testGatherv = .false.
               return
            endif
            if (rank.ne.0.and.recvBuff(i).ne.0) then
               testGatherv = .false.
               return
            endif
            j = j + 1
         enddo
         testGatherv = .true.
      end

      logical function testScatterv(rank, size, buffSize)
         include 'mpif.h'
         integer summation
         integer rank, size, buffSize, i, sendCounts(size), displs(size)
         real sendBuff(buffSize), recvBuff(rank + 1)
         do i = 1, size
            sendCounts(i) = i
            displs(i) = summation(i - 1)
         enddo
         if (rank.eq.0) then
            do i = 1, buffSize
               sendBuff(i) = 10 * i
            enddo
         endif
         call MPI_SCATTERV(sendBuff, sendCounts, displs, MPI_REAL, 
     +                     recvBuff, rank + 1, MPI_REAL, 0, 
     +                     MPI_COMM_WORLD, ierr); 
         do i = 1, rank + 1
            if (recvBuff(i).ne.summation(rank) * 10 + 10 * i) then
               testScatterv = .false.
               return
            endif
         enddo
         testScatterv = .true.
      end

      logical function testAllgatherv(rank, size, buffSize)
         include 'mpif.h'
         integer summation
         integer rank, size, buffSize, i, recvCounts(size), displs(size)
         real recvBuff(buffSize), sendBuff(rank + 1), expected, j
         j = 1
         do i = 1, size
            recvCounts(i) = i
            displs(i) = summation(i - 1)
         enddo
         do i = 1, rank + 1
            sendBuff(i) = (10 * (rank + 1)) + i - 1
         enddo
         call MPI_ALLGATHERV(sendBuff, rank + 1, MPI_REAL, recvBuff, 
     +                       recvCounts, displs, MPI_REAL, 
     +                       MPI_COMM_WORLD, ierr);       
         do i = 1, buffSize
         if (MOD(recvBuff(i),10 * j).eq.0) then
            expected = 10 * i
         else
            expected = MOD(recvBuff(i),10 * j)
         endif
         if (recvBuff(i).ne.expected) then
            testAllgatherv = .false.
            return
         endif
         j = j + 1
         enddo
         testAllgatherv = .true.
      end

      logical function testAlltoallv(rank, size, buffSize)
         include 'mpif.h'
         integer summation
         real computeBase, computeValue
         integer rank, size, buffSize, sendCounts(size) 
         integer recvCounts(size), sDispls(size), rDispls(size), i
         real sendBuff((rank + 1) * size), recvBuff(buffSize)
         do i = 1, (rank + 1) * size
            sendBuff(i) = rank * 100 + i - 1
         enddo
         do i = 1, size
            sendCounts(i) = rank + 1
            recvCounts(i) = i 
            sDispls(i) = (rank + 1) * (i - 1)
            rDispls(i) = summation(i - 1);
         enddo
         call MPI_ALLTOALLV(sendBuff, sendCounts, sDispls, MPI_REAL, 
     +                      recvBuff, recvCounts, rDispls, MPI_REAL, 
     +                      MPI_COMM_WORLD, ierr);
         do i = 1, buffSize
            if (recvBuff(i).ne.100 * computeBase(i) + 
     +                         computeValue(rank, i)) then
               testAlltoallv = .false.
               return
            endif 
         enddo
         testAlltoallv = .true.
      end

      logical function testReduce_scatter(rank, size)
         include 'mpif.h'
         integer rank, size, recvCounts(size), i
         real recv, sendBuff(size), expected
         expected = 0
         do i = 1, size
            sendBuff(i) = size * rank + i - 1;
            recvCounts(i) = 1;
         enddo
         call MPI_REDUCE_SCATTER(sendBuff, recv, recvCounts, MPI_REAL, 
     +                           MPI_SUM, MPI_COMM_WORLD, ierr);
         do i = 0, size - 1
            expected = expected + i * size + rank
         enddo
         if (recv.ne.expected) then
            testReduce_scatter = .false.
            return
         endif
         testReduce_scatter = .true.
      end

********* MPIComtor ***********

      logical function testComm_create(rank, size)
         include 'mpif.h'
c        MPI_COMM_CREATE must be created with an existing communicator 
c        and must be called by every process in that communicator
         logical result
         integer rank, size, ranks1(2), ranks2(size - 2), i
         integer worldCommGroup, newGroup, comm
         character buffer*64, msg*64
         buffer = ''
         msg = 'BCAST message from process 0 to all process on new comm'
         ranks1(1) = 0
         ranks1(2) = 1
         result = .true.
         do i = 2, size
            ranks2(i - 1) = i
         enddo
         call MPI_COMM_GROUP(MPI_COMM_WORLD, worldCommGroup, ierr)
         if (rank.eq.0.or.rank.eq.1) then
            call MPI_GROUP_INCL(worldCommGroup, 2, ranks1, newGroup, 
     +                          ierr)
         else
            call MPI_GROUP_INCL(worldCommGroup, size - 2, ranks2, 
     +                          newGroup, ierr)
         endif
         call MPI_COMM_CREATE(MPI_COMM_WORLD, newGroup, comm, ierr)
         if (rank.eq.0) then
            call MPI_BCAST(msg, 64, MPI_BYTE, 0, comm, ierr);
         else
            call MPI_BCAST(buffer, 64, MPI_BYTE, 0, comm, ierr);
            if (rank.eq.1.and.buffer.ne.msg) then
               result = .false.
            endif
            if (rank.ne.1.and.buffer.ne.'') then
               result = .false.
            endif
         endif
         call MPI_COMM_FREE(comm, ierr)
         testComm_create = result
      end

      logical function testComm_dup(rank)
         include 'mpif.h'
         logical result
         integer rank, dupCommWorld
         character buffer*64,  msg*64
         msg = 'BCAST message from process 0 to all processes on dup'//
     +         ' comm'
         result = .true.
         call MPI_COMM_DUP(MPI_COMM_WORLD, dupCommWorld, ierr)
         if (rank.eq.0) then
            call MPI_BCAST(msg, 64, MPI_BYTE, 0, dupCommWorld, ierr)
         else
            call MPI_BCAST(buffer, 64, MPI_BYTE, 0, dupCommWorld, ierr)
            if (buffer.ne.msg) then
               result = .false.
            endif
         endif
         call MPI_COMM_FREE(dupCommWorld, ierr)
         testComm_dup = result
      end

      logical function testComm_split(rank)
         include 'mpif.h'
         logical result
         integer rank, color, comm
         character buffer*64, msg*64
         msg = 'BCAST message from process 0 to all processes on '//
     +         'split comm'
         if (MOD(rank,2).eq.0) then
            color = 2
         else
            color = 1
         endif  
         result = .true.   
         call MPI_COMM_SPLIT(MPI_COMM_WORLD, color, rank, comm, ierr)
         if (rank.eq.0.or.rank.eq.1) then
            call MPI_BCAST(msg, 64, MPI_BYTE, 0, comm, ierr)
         else
            call MPI_BCAST(buffer, 64, MPI_BYTE, 0, comm, ierr)
            if (buffer.ne.msg) then
               result = .false.
            endif
         endif
         call MPI_COMM_FREE(comm, ierr)
         testComm_split = result
      end

      logical function testComm_free(rank)
         include 'mpif.h'
         logical result
         integer rank, dupCommWorld, tag, status(MPI_STATUS_SIZE) 
         character buffer*64, msg*64
         msg = 'SEND message from process 0 to process 1 on dup comm'
         tag = 20
         result = .true.
         call MPI_COMM_DUP(MPI_COMM_WORLD, dupCommWorld, ierr)
         if (rank.eq.0) then
            call MPI_SEND(msg, 64, MPI_BYTE, 1, tag, dupCommWorld, ierr)
         else if (rank.eq.1) then
            call MPI_RECV(buffer, 64, MPI_BYTE, 0, tag, dupCommWorld, 
     +                    status, ierr)
            if (buffer.ne.msg) then
               result = .false.
            endif
         endif
         if (dupCommWorld.eq.MPI_COMM_NULL) then
            result = .false.
         endif
         call MPI_COMM_FREE(dupCommWorld, ierr)
         if (dupCommWorld.ne.MPI_COMM_NULL) then
            result = .false.
         endif       
         testComm_free = result
      end

********* MPIMarker ***********

      logical function testPcontrol(rank)
         include 'mpif.h'
         integer rank, level, ierr
         if (rank.eq.0) then
            level = 0
         else if (rank.eq.1) then
            level = 1
         else if (rank.eq.2) then
            level = 2
         endif
c        All other values of level have profile library
c        defined effects and additional arguments
         call MPI_PCONTROL(level, ierr)
         if (ierr.ne.MPI_SUCCESS) then
            testPcontrol = .false.
            return
         endif 
         testPcontrol = .true.
      end

********* MPIP2PComm ***********

      logical function testSend()
         include 'mpif.h'
         character buff1*64, buff2*102400, buff3*262144
         character MSG1*64, MSG2*102400, MSG3*262144 
         common /global_1/ MSG1, MSG2, MSG3
         buff1 = MSG1
         buff2 = MSG2  
         buff3 = MSG3 
         call MPI_SEND(buff1, 64, MPI_BYTE, 1, 1, MPI_COMM_WORLD, ierr) 
         call MPI_SEND(buff2, 100*1024, MPI_BYTE, 1, 1, MPI_COMM_WORLD, 
     +                 ierr) 
         call MPI_SEND(buff3, 256*1024, MPI_BYTE, 1, 1, MPI_COMM_WORLD, 
     +                 ierr)             
         testSend = .true.
      end

      logical function testRecv()
         include 'mpif.h'
         logical result
         integer status(MPI_STATUS_SIZE)
         character buff1*64, buff2*102400, buff3*262144
         character MSG1*64, MSG2*102400, MSG3*262144 
         common /global_1/ MSG1, MSG2, MSG3
         result = .true.
         call MPI_RECV(buff1, 64, MPI_BYTE, 0, 1, MPI_COMM_WORLD, 
     +                 status, ierr)
         if (buff1.ne.MSG1) then
            result = .false.
         endif
         call MPI_RECV(buff2, 100*1024, MPI_BYTE, 0, 1, MPI_COMM_WORLD, 
     +                 status, ierr)
         if (buff2.ne.MSG2) then
            result = .false.
         endif
         call MPI_RECV(buff3, 256*1024, MPI_BYTE, 0, 1, MPI_COMM_WORLD, 
     +                 status, ierr)
         if (buff3.ne.MSG3) then
            result = .false.
         endif
         testRecv = result
      end

      logical function testBsend(rank, buffSize)
         include 'mpif.h'
         logical result
         integer rank, tag, buffSize, status(MPI_STATUS_SIZE)
         character sendBuff*64, recvBuff* 64, msg1*64, msg2*64
         character buffer(buffSize)
         msg1 = 'BSEND 64 byte message from process 0 to process 1'
         msg2 = 'BSEND has returned and buffer has been modified'
         tag = 30
         result = .true.
         if (rank.eq.0) then
            call MPI_BUFFER_ATTACH(buffer, buffSize, ierr)
            if (ierr.ne.MPI_SUCCESS) then
               result = .false.
            endif
            sendBuff = MSG1
            call MPI_BSEND(sendBuff, 64, MPI_BYTE, 1, tag, 
     +                     MPI_COMM_WORLD, ierr)
c           Test that the sendbuffer can be immediately used again
            sendBuff = MSG2
            call MPI_RSEND(sendBuff, 64, MPI_BYTE, 1, tag + 1, 
     +                     MPI_COMM_WORLD, ierr)   
            call MPI_BUFFER_DETACH(buffer, buffSize, ierr)
         else if (rank.eq.1) then
            call MPI_RECV(recvBuff, 64, MPI_BYTE, 0, tag + 1, 
     +                    MPI_COMM_WORLD, status, ierr)
            if (recvBuff.ne.MSG2) then
               result = .false.
            endif
            call MPI_RECV(recvBuff, 64, MPI_BYTE, 0, tag, 
     +                    MPI_COMM_WORLD, status, ierr)
            if (recvBuff.ne.MSG1) then
               result = .false.
            endif
         endif
         testBsend = result
      end

      logical function testRsend(rank)
         include 'mpif.h'
         logical result
         integer rank, tag, status(MPI_STATUS_SIZE)
         double precision t0, t1
         character recvBuff*64, msg*64
         msg = 'RSEND 64 byte message from process 0 to process 1'
         tag = 40
         result = .true.
         if (rank.eq.0) then
            call MPI_RSEND(msg, 64, MPI_BYTE, 1, tag, MPI_COMM_WORLD, 
     +                     ierr)
c           Test that RSEND returns before the receive is initiated 
            t0 = MPI_WTIME()
            call MPI_SEND(t0, 1, MPI_DOUBLE_PRECISION, 1, tag + 1, 
     +                    MPI_COMM_WORLD, ierr)
         else if (rank.eq.1) then
            call sleep(1)
            t1 = MPI_WTIME()
            call MPI_RECV(recvBuff, 64, MPI_BYTE, 0, tag, 
     +                    MPI_COMM_WORLD, status, ierr)
            if (recvBuff.ne.msg) then
               result = .false.
            endif        
            call MPI_Recv(t0, 1, MPI_DOUBLE_PRECISION, 0, tag + 1,  
     +                    MPI_COMM_WORLD, status, ierr)
            if (t1.le.t0) then
               result = .false.
            endif
         endif
         testRsend = result
      end

      logical function testSsend(rank)
         include 'mpif.h'
         integer rank, tag, status(MPI_STATUS_SIZE)
         double precision t0, t1a, t1b
         character recvBuff*64, msg*64
         msg = 'SSEND 64 byte message from process 0 to process 1'
         tag = 50 
         call MPI_BARRIER(MPI_COMM_WORLD, ierr)
         if (rank.eq.0) then
            call MPI_SSEND(msg, 64, MPI_BYTE, 1, tag, MPI_COMM_WORLD, 
     +                     ierr)
c           Test that SSEND returns after the receive is initiated
            t0 = MPI_WTIME()
            call MPI_SEND(t0, 1, MPI_DOUBLE_PRECISION, 1, tag + 1, 
     +                    MPI_COMM_WORLD, ierr)
         else if (rank.eq.1) then
            call sleep(1)
            t1a = MPI_WTIME()
            call MPI_IRECV(recvBuff, 64, MPI_BYTE, 0, tag, 
     +                     MPI_COMM_WORLD, status, ierr)
            t1b = MPI_WTIME()
            call MPI_RECV(t0, 1, MPI_DOUBLE_PRECISION, 0, tag + 1, 
     +                    MPI_COMM_WORLD, status, ierr)
            if (t0.lt.t2a.or.ABS(t1b - t0).gt.0.25) then
               testSsend = .false.
               return
            endif
            if (recvBuff.ne.msg) then
               testSsend = .false.
               return
            endif
         endif
         testSsend = .true.
      end

      logical function testIsend(rank)
         include 'mpif.h'
         integer rank, request
         character MSG4*64, MSG5*64
         common /global_2/ MSG4, MSG5
         call MPI_ISEND(MSG4, 64, MPI_BYTE, 1, 4, MPI_COMM_WORLD, 
     +                  request, ierr)
         call MPI_SEND(MSG5, 64, MPI_BYTE, 1, 5, MPI_COMM_WORLD, ierr)
         testIsend = .true.
      end

      logical function testIrecv(rank)
         include 'mpif.h'
         logical result
         integer rank, status(MPI_STATUS_SIZE)
         character recvBuff*64, MSG4*64, MSG5*64, MSG6*64
         common /global_2/ MSG4, MSG5
         common /global_3/ MSG6
         recvBuff = ''
         result = .true.
c        Test that IRECV is non-blocking
         call MPI_IRECV(recvBuff, 64, MPI_BYTE, 0, 6, MPI_COMM_WORLD, 
     +                  status, ierr)
         if (recvBuff.ne.'') then
            result = .false.
         endif 
         call MPI_RECV(recvBuff, 64, MPI_BYTE, 0, 5, MPI_COMM_WORLD, 
     +                 status, ierr)
         if (recvBuff.ne.MSG5) then
            result = .false.
         endif  
         call MPI_Irecv(recvBuff, 64, MPI_BYTE, 0, 4, MPI_COMM_WORLD, 
     +                  status, ierr)
         if (recvBuff.ne.MSG4) then
            result = .false.
         endif  
         testIrecv = result
      end

      logical function testIbsend(rank, buffSize)
         include 'mpif.h'
         logical result
         integer rank, buffSize, tag, request, status(MPI_STATUS_SIZE)
         character sendBuff*64, recvBuff*64, msg1*64, msg2*64
         character buffer(buffSize)
         msg1 = 'IBSEND 64 byte message from process 0 to process 1'
         msg2 = 'IBSEND has returned and buffer has been modified'
         tag = 60
         result = .true.
         if (rank.eq.0) then
            call MPI_BUFFER_ATTACH(buffer, buffSize, ierr)
            if (ierr.ne.MPI_SUCCESS) then
               result = .false.
            endif
            sendBuff = msg1
            call MPI_IBSEND(sendBuff, 64, MPI_BYTE, 1, tag, 
     +                      MPI_COMM_WORLD, request, ierr)
c           Test that the sendbuffer can be immediately used again
            sendBuff = msg2
            call MPI_RSEND(sendBuff, 64, MPI_BYTE, 1, tag + 1, 
     +                     MPI_COMM_WORLD, ierr)
            call MPI_BUFFER_DETACH(buffer, size, ierr)
         else if (rank.eq.1) then
            call MPI_RECV(recvBuff, 64, MPI_BYTE, 0, tag + 1, 
     +                    MPI_COMM_WORLD, status, ierr)
            if (recvBuff.ne.msg2) then
               result = .false.
            endif
            call MPI_RECV(recvBuff, 64, MPI_BYTE, 0, tag, 
     +                    MPI_COMM_WORLD, status, ierr)
            if (recvBuff.ne.msg1) then
               result = .false.
            endif
         endif
         testIbsend = result
      end

      logical function testIssend(rank)
         include 'mpif.h'
         logical result
         integer rank, tag, request, status(MPI_STATUS_SIZE)
         character recvBuff*64, msg1*64, msg2*64
         msg1 = 'ISSEND 64 byte message from process 0 to process 1'
         msg2 = 'Call to ISSEND has returned'
         tag = 70
         result = .true.   
         call MPI_BARRIER(MPI_COMM_WORLD, ierr)
         if (rank.eq.0) then
            call MPI_ISSEND(msg1, 64, MPI_BYTE, 1, tag, MPI_COMM_WORLD,
     +                      request, ierr)
c           Test that ISSEND is non-blocking 
            call MPI_SEND(msg2, 64, MPI_BYTE, 1, tag + 1,
     +                    MPI_COMM_WORLD, request, ierr)
         else if (rank.eq.1) then 
            call MPI_RECV(recvBuff, 64, MPI_BYTE, 0, tag + 1, 
     +                    MPI_COMM_WORLD, status, ierr)
            if (recvBuff.ne.msg2) then
               result = .false.
            endif 
            call MPI_RECV(recvBuff, 64, MPI_BYTE, 0, tag, 
     +                    MPI_COMM_WORLD, status, ierr)
            if (recvBuff.ne.msg1) then
               result = .false.
            endif
         endif
         testIssend = result
      end

      logical function testIrsend(rank)
         include 'mpif.h'
         logical result
         integer rank, tag, request, status(MPI_STATUS_SIZE)
         double precision t0, t1
         character recvBuff*64, msg1*64, msg2*64
         msg1 = 'IRSEND 64 byte message from process 0 to process 1'
         msg2 = 'Receive message from process 1'
         tag = 80
         result = .true.  
         if (rank.eq.0) then 
c           Test that IRSEND returns before the receive is initiated 
            call MPI_RECV(t1, 1, MPI_DOUBLE_PRECISION, 1, tag, 
     +                    MPI_COMM_WORLD, status, ierr)
            call sleep(1)
            call MPI_IRSEND(msg1, 64, MPI_BYTE, 1, tag + 1, 
     +                      MPI_COMM_WORLD, request, ierr)
            t0 = MPI_WTIME()
            if (t1.ge.t0) then
               result = .false.
            endif
c           Test that IRSEND is non-blocking 
            call MPI_RECV(recvBuff, 64, MPI_BYTE, 1, tag + 2, 
     +                    MPI_COMM_WORLD, status, ierr)
            if (recvBuff.ne.msg2) then
               result = .false.
            endif
         else if (rank.eq.1) then 
            call MPI_Send(msg2, 64, MPI_BYTE, 0, tag + 2, 
     +                    MPI_COMM_WORLD, ierr)
            t1 = MPI_WTIME()
            call MPI_Send(t1, 1, MPI_DOUBLE_PRECISION, 0, tag, 
     +                    MPI_COMM_WORLD, ierr) 
            call MPI_Recv(recvBuff, 64, MPI_BYTE, 0, tag + 1, 
     +                    MPI_COMM_WORLD, status, ierr)
            if (recvBuff.ne.msg1) then
               result = .false.
            endif
         endif
         testIrsend = result
      end

      logical function testStart(rank)
         include 'mpif.h'
         logical result
         integer rank, tag, i, request, status(MPI_STATUS_SIZE), REPS
         common /global_4/ REPS
         character recvBuff*64, msg*64
***********************************************************************
* NOTE This test causes collective abort of all ranks in fCountTest and 
*      fTraceTest, but works in fTimerTest and fLightTest. This code 
*      should be removed once the bug in PSiNSTracer is fixed.
         testStart = .false.
         return
***********************************************************************
         msg = 'SEND 64 byte message from process 0 to process 1'
         tag = 90 
         result = .true.
         if (rank.eq.0) then 
            call MPI_SEND_INIT(msg, 64, MPI_BYTE, 1, tag, 
     +                         MPI_COMM_WORLD, request, ierr)
            do i = 1, REPS
               call MPI_START(request, ierr)
               call MPI_WAIT(request, status, ierr)
            enddo
            call MPI_REQUEST_FREE(request)
         else if (rank.eq.1) then 
            do i = 1, REPS
               call MPI_RECV(recvBuff, 64, MPI_BYTE, 0, tag, 
     +                       MPI_COMM_WORLD, status, ierr)
               if (recvBuff.ne.msg) then
                  result = .false.
               endif
            enddo
         endif
         testStart = result
      end

      logical function testWait(rank)
         include 'mpif.h'
         integer rank, tag, request, status(MPI_STATUS_SIZE)
         character recvBuff*64, msg*64
         msg = 'ISEND 64 byte message from process 0 to process 1'
         tag = 100 
         if (rank.eq.0) then 
            call sleep(1)
            call MPI_SEND(msg, 64, MPI_BYTE, 1, tag, MPI_COMM_WORLD, 
     +                    ierr)
         else if (rank.eq.1) then 
            call MPI_IRECV(recvBuff, 64, MPI_BYTE, 0, tag, 
     +                     MPI_COMM_WORLD, request, ierr)
            call MPI_WAIT(request, status, ierr)
            if (recvBuff.ne.msg) then
               testWait = .false.
               return
            endif
         endif
         testWait = .true.
      end

      logical function testStartall(rank)
         include 'mpif.h'
         logical result
         integer rank, i, request(2), status(MPI_STATUS_SIZE, 2), REPS
         integer r_status
         common /global_4/ REPS
         character recvBuff1*64, recvBuff2*64, msg1*64, msg2*64
         msg1 = 'SEND 64 byte message from process 0 to process 1'//
     +          ' with tag 90'
         msg2 = 'SEND 64 byte message from process 0 to process 1'//
     +          ' with tag 91'
         result = .true.
         if (rank.eq.0) then  
            call MPI_SEND_INIT(msg1, 64, MPI_BYTE, 1, 90, 
     +                         MPI_COMM_WORLD, request(1), ierr)
            call MPI_SEND_INIT(msg2, 64, MPI_BYTE, 1, 91, 
     +                         MPI_COMM_WORLD, request(2), ierr)
            do i = 1, REPS
               call MPI_STARTALL(2, request, ierr)
               call MPI_WAITALL(2, request, status, ierr)
            enddo 
            call MPI_REQUEST_FREE(request(1), ierr)
            call MPI_REQUEST_FREE(request(2), ierr) 
         else if (rank.eq.1) then 
            do i = 1, REPS
               call MPI_IRECV(recvBuff1, 64, MPI_BYTE, 0, 90, 
     +                        MPI_COMM_WORLD, request(1), ierr) 
               call MPI_RECV(recvBuff2, 64, MPI_BYTE, 0, 91, 
     +                       MPI_COMM_WORLD, r_status, ierr)
               if (recvBuff2.ne.msg2) then
                  result = .false.
               endif
               call MPI_Wait(request(1), r_status, ierr)
               if (recvBuff1.ne.msg1) then
                  result = .false.
               endif
            enddo 
         endif
         testStartall = result
      end

      logical function testWaitall(rank, size)
         include 'mpif.h'
         integer rank, size, tag, i, status(MPI_STATUS_SIZE, size - 1)
         integer request(size - 1)
         character sendBuff*64, recvBuff(size - 1)*64, buff*64
         tag = 120
 10      format('SEND 64 byte message from process ',I3.3,
     +          ' to process 0 with tag ',I3)
         if (rank.eq.0) then 
            do i = 1, size - 1
               call MPI_IRECV(recvBuff(i), 64, MPI_BYTE, i, tag + i, 
     +                        MPI_COMM_WORLD, request(i), ierr)
            enddo
            call MPI_WAITALL(size - 1, request, status, ierr)
            do i = 1, size - 1
               write(buff,10) i, tag + i
               if (recvBuff(i).ne.buff) then
                  testWaitall = .false.
                  return
               endif
            enddo
         else
            write(sendBuff,10) rank, tag + rank
            call MPI_SEND(sendBuff, 64, MPI_BYTE, 0, tag + rank, 
     +                    MPI_COMM_WORLD, ierr)
         endif
         testWaitall = .true.
      end

      logical function testWaitany(rank, size)
         include 'mpif.h'
         integer rank, size, tag, i, status(MPI_STATUS_SIZE, size - 1)
         integer request(size - 1), index
         character sendBuff*64, recvBuff(size - 1)*64, buff*64
         tag = 130
 20      format('ISEND 64 byte message from process ',I3.3,
     +          ' to process 0 with tag ',I3)
         if (rank.eq.0) then 
            do i = 1, size - 1
               call MPI_IRECV(recvBuff(i), 64, MPI_BYTE, i, tag + i, 
     +                        MPI_COMM_WORLD, request(i), ierr)
            enddo
            do i = 1, size - 1
               call MPI_WAITANY(size - 1, request, index, status, ierr)
            enddo
***********************************************************************
* NOTE This test passes in fTimerTest and fLightTest but in fCountTest 
*      and fTraceTest MPI_WAITANY can return an index of 0. The standard 
*      specifies that the range is 1 to count. This code can be removed
*      once the bug in PSiNSTracer is fixed.
            if (index.eq.0) then
               testWaitany = .false.
               return
            endif
***********************************************************************
            write(buff,20) index, tag + index
            if (recvBuff(index).ne.buff) then 
               testWaitany = .false.
               return
            endif          
         else
            write(sendBuff,20) rank, tag + rank
            call MPI_ISEND(sendBuff, 64, MPI_BYTE, 0, tag + rank, 
     +                    MPI_COMM_WORLD, request(1), ierr)
         endif
         testWaitany = .true.
      end

      logical function testWaitsome(rank, size)
         include 'mpif.h'
         integer rank, size, tag, i, status(MPI_STATUS_SIZE, size - 1)
         integer request(size - 1), indices(size - 1), remaining, count
         integer index
         character sendBuff*64, recvBuff(size - 1)*64, buff*64
         tag = 140
         remaining = size - 1
 30      format('ISEND 64 byte message from process ',I3.3,
     +          ' to process 0 with tag ',I3)
         if (rank.eq.0) then 
            do i = 1, size - 1
               call MPI_IRECV(recvBuff(i), 64, MPI_BYTE, i, tag + i, 
     +                        MPI_COMM_WORLD, request(i), ierr)
            enddo
 40         if (remaining.gt.0) then
               call MPI_WAITSOME(size - 1, request, count, indices, 
     +                           status, ierr)
               if (count.gt.0) then
                  remaining = remaining - count
               endif
               goto 40
            endif
            do i = 1, count
               index = indices(i)
***********************************************************************
* NOTE This test passes in fTimerTest and fLightTest but in fCountTest 
*      and fTraceTest MPI_WAITSOME can return an index of 0. The standard 
*      specifies that the range is 1 to count. This code can be removed
*      once the bug in PSiNSTracer is fixed.
               if (index.eq.0) then
                  testWaitsome = .false.
                  return
               endif
***********************************************************************
               write(buff,30) index, tag + index
               if (recvBuff(index).ne.buff) then
                  testWaitsome = .false.
                  return
               endif
            enddo
         else
            write(sendBuff,30) rank, tag + rank
            call MPI_ISEND(sendBuff, 64, MPI_BYTE, 0, tag + rank, 
     +                    MPI_COMM_WORLD, request(1), ierr)
         endif
         testWaitsome = .true.
      end

      logical function testSendrecv(rank)
         include 'mpif.h'
         integer rank, source, dest, sendTag, recvTag 
         integer status(MPI_STATUS_SIZE)
         character msg1*64, msg2*64, sendBuff*64, recvBuff*64
         msg1 = 'SEND 64 byte message from process 0 to process 1'//
     +          ' with tag 150'
         msg2 = 'SEND 64 byte message from process 1 to process 0'//
     +          ' with tag 151'
         if (rank.eq.0) then 
            dest = 1
            source = 1
            sendTag = 150
            recvTag = 151
            sendBuff = msg1
         else if (rank.eq.1) then 
            dest = 0
            source = 0
            sendTag = 151
            recvTag = 150
            sendBuff = msg2
         endif
         call MPI_SENDRECV(sendBuff, 64, MPI_BYTE, dest, sendTag, 
     +                     recvBuff, 64, MPI_BYTE, source, recvTag, 
     +                     MPI_COMM_WORLD, status, ierr)
         if (rank.eq.0.and.recvBuff.ne.msg2) then
            testSendrecv = .false.
            return
         endif
         if (rank.eq.1.and.recvBuff.ne.msg1) then
            testSendrecv = .false.
            return
         endif
         testSendrecv = .true.
      end

********* MPIReqinit ***********

      logical function testSend_init()
         include 'mpif.h'
         integer request, i, status(MPI_STATUS_SIZE), REPS
         character MSG7*64
         common /global_4/ REPS
         common /global_5/ MSG7
         call MPI_SEND_INIT(MSG7, 64, MPI_BYTE, 1, 7, 
     +                      MPI_COMM_WORLD, request, ierr)
         do i = 1, REPS
            call MPI_START(request, ierr)
            call MPI_WAIT(request, status, ierr)
         enddo
         call MPI_REQUEST_FREE(request, ierr) 
         testSend_init = .true.
      end

      logical function testRecv_init()
         include 'mpif.h'
         logical result
         integer request, i, status(MPI_STATUS_SIZE), REPS
         character recvBuff*64, MSG7*64
         common /global_4/ REPS
         common /global_5/ MSG7
         result = .true.
         call MPI_RECV_INIT(recvBuff, 64, MPI_BYTE, 0, 7, 
     +                      MPI_COMM_WORLD, request, ierr)
         do i = 1, REPS
            call MPI_START(request, ierr)
            call MPI_WAIT(request, status, ierr)
            if (recvBuff.ne.MSG7) then
               result = .false.
            endif
         enddo
         call MPI_REQUEST_FREE(request, ierr) 
         testRecv_init = result
      end

      logical function testBsend_init(rank, buffSize)
         include 'mpif.h'
         logical result
         integer rank, buffSize, request, i, tag, ierr
         integer status(MPI_STATUS_SIZE), REPS
         common /global_4/ REPS
         character msg1*64, msg2*64, sendBuff*64, recvBuff(REPS)*64
         character buffer(buffSize)
         msg1 = 'BSEND 64 byte message from process 0 to process 1'
         msg2 = 'BSEND has started and buffer has been modified'
         tag = 160
         result = .true.
         if (rank.eq.0) then 
            call MPI_BUFFER_ATTACH(buffer, buffSize, ierr)
            if (ierr.ne.MPI_SUCCESS) then
               result = .false.
            endif
            call MPI_BSEND_INIT(sendBuff, 64, MPI_BYTE, 1, tag, 
     +                          MPI_COMM_WORLD, request, ierr)
            do i = 1, REPS
               sendBuff = msg1
               call MPI_START(request, ierr)
c              Test that the sendbuffer can be used again
               sendBuff = msg2
               call MPI_RSEND(sendBuff, 64, MPI_BYTE, 1, tag + 1, 
     +                        MPI_COMM_WORLD, ierr)
               call MPI_WAIT(request, status, ierr)
            enddo
            call MPI_BUFFER_DETACH(buffer, buffSize, ierr)
            call MPI_REQUEST_FREE(request, ierr)
         else if (rank.eq.1) then 
            do i = 1, REPS
               call MPI_RECV(recvBuff(i), 64, MPI_BYTE, 0, tag + 1, 
     +                       MPI_COMM_WORLD, status, ierr)
               if (recvBuff(i).ne.msg2) then
                  result = .false.
               endif
               call MPI_RECV(recvBuff(i), 64, MPI_BYTE, 0, tag, 
     +                       MPI_COMM_WORLD, status, ierr)
               if (recvBuff(i).ne.msg1) then
                  result = .false.
               endif
            enddo
         endif
         testBsend_init = result
      end

      logical function testRsend_init(rank)
         include 'mpif.h'
         logical result
         integer rank, request, i, tag, status(MPI_STATUS_SIZE), REPS
         common /global_4/ REPS
         double precision t0, t1
         character msg*64, recvBuff(REPS)*64
         msg = 'IRSEND 64 byte message from process 0 to process 1'
         tag = 170
         result = .true.
         if (rank.eq.0) then
            call MPI_RSEND_INIT(msg, 64, MPI_BYTE, 1, tag, 
     +                          MPI_COMM_WORLD, request, ierr) 
            do i = 1, REPS
               call MPI_START(request, ierr)
c              Test that Rsend returns before the receive is initiated
               t0 = MPI_WTIME()
               call MPI_SEND(t0, 1, MPI_DOUBLE_PRECISION, 1, tag + 1, 
     +                       MPI_COMM_WORLD, ierr)
               call MPI_WAIT(request, status, ierr)
            enddo
            call MPI_REQUEST_FREE(request, ierr)
         else if (rank.eq.1) then 
            do i = 1, REPS
               call sleep(1)
               t1 = MPI_WTIME()
               call MPI_RECV(recvBuff(i), 64, MPI_BYTE, 0, tag, 
     +                       MPI_COMM_WORLD, status, ierr)
               if (recvBuff(i).ne.msg) then
                  result = .false.
               endif
               call MPI_RECV(t0, 1, MPI_DOUBLE_PRECISION, 0, tag + 1, 
     +                       MPI_COMM_WORLD, status, ierr)
               if (t1.le.t0) then
                  result = .false.
               endif
            enddo
         endif
         testRsend_init = result
      end

      logical function testSsend_init(rank)
         include 'mpif.h'
         logical result
         integer rank, request, i, tag, status(MPI_STATUS_SIZE), REPS
         common /global_4/ REPS
         character msg*64, recvBuff(REPS)*64
         msg = 'ISSEND 64 byte message from process 0 to process 1'
         tag = 180
         result = .true.
         if (rank.eq.0) then
            call MPI_SSEND_INIT(msg, 64, MPI_BYTE, 1, tag, 
     +                          MPI_COMM_WORLD, request, ierr)
            do i = 1, REPS
               call MPI_START(request, ierr)
               call MPI_WAIT(request, status, ierr)
            enddo
            call MPI_REQUEST_FREE(request, ierr)
         else if (rank.eq.1) then 
            do i = 1, REPS
               call MPI_RECV(recvBuff(i), 64, MPI_BYTE, 0, tag, 
     +                       MPI_COMM_WORLD, status, ierr)
               if (recvBuff(i).ne.msg) then
                  result = .false.
               endif
            enddo
         endif
         testSsend_init = result
      end

      logical function testRequest_free(rank)
         include 'mpif.h'
         integer rank, request, i, tag, status(MPI_STATUS_SIZE), REPS
         common /global_4/ REPS
         character msg*64, recvBuff(REPS)*64
         msg = 'SEND 64 byte message from process 0 to process 1'
         tag = 190
         if (rank.eq.0) then
            call MPI_SEND_INIT(msg, 64, MPI_BYTE, 1, tag, 
     +                         MPI_COMM_WORLD, request, ierr)
            do i = 1, REPS
               call MPI_START(request, ierr)
               call MPI_WAIT(request, status, ierr)
            enddo
            if (request.eq.MPI_REQUEST_NULL) then
               testRequest_free = .false.
               return
            endif
            call MPI_REQUEST_FREE(request, ierr)
            if (request.ne.MPI_REQUEST_NULL) then
               testRequest_free = .false.
               return
            endif
         else if (rank.eq.1) then 
            do i = 1, REPS
               call MPI_RECV(recvBuff, 64, MPI_BYTE, 0, tag, 
     +                       MPI_COMM_WORLD, status, ierr)
            enddo
         endif
         testRequest_free = .true.
      end

      logical function testMPI(rank, size)
         include 'mpif.h'
         logical testBarrier, testBcast, testReduce, testScan
         logical testAllreduce, testGather, testScatter, testAllgather
         logical testAlltoall, testGatherv, testScatterv, testAllgatherv
         logical testAlltoallv, testReduce_scatter, testComm_create
         logical testComm_dup, testComm_split, testComm_free
         logical testPcontrol, testSend, testRecv, testBsend, testRsend
         logical testSsend, testIsend, testIrecv, testIbsend, testIssend
         logical testIrsend, testStart, testWait, testStartall
         logical testWaitall, testWaitany, testWaitsome, testSendrecv
         logical testSend_init, testRecv_init, testBsend_init
         logical testRsend_init, testSsend_init, testRequest_free
         integer summation
         integer rank, size, buffSize, i, ierr 
         logical result, recvBuff(size)
         character MSG1*64, MSG2*102400, MSG3*262144
         character MSG4*64, MSG5*64, MSG6*64, MSG7*64
         integer REPS
         common /global_1/ MSG1, MSG2, MSG3
         common /global_2/ MSG4, MSG5
         common /global_3/ MSG6
         common /global_4/ REPS
         common /global_5/ MSG7
         MSG1 = 'SEND 64 byte message from process 0 to process 1'//
     +          ' with tag 1'
         MSG2 = 'SEND 100k message from process 0 to process 1'//
     +          ' with  tag 2'
         MSG3 = 'SEND 256k message from process 0 to process 1'//
     +          ' with tag 3'
         MSG4 = 'ISEND 64 byte message from process 0 to process 1'//
     +          ' with tag 4'
         MSG5 = 'Call to Isend has returned - tag 5'
         MSG6 = 'This message will never be sent - tag 6'
         MSG7 = 'SEND 64 byte message from process 0 to process 1'//
     +          ' with tag 7'
         REPS = 50  
         result = .true.
         if (size < 4) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'ERROR: At least 4 processes are required',
     +                 ' for MPI testing'
            endif
            go to 99
         endif
         if (.not.testBarrier(rank)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_BARRIER test failed'
            endif
         endif
         if (.not.testBcast(rank)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_BCAST test failed'
            endif
         endif
         if (.not.testReduce(rank, size)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_REDUCE test failed'
            endif
         endif
         if (.not.testScan(rank, size)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_SCAN failed'
            endif
         endif
         if (.not.testAllreduce(rank, size)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_ALLREDUCE failed'
            endif
         endif
         if (.not.testGather(rank, size)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_GATHER test failed'
            endif
         endif
         if (.not.testScatter(rank, size)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_SCATTER test failed'
            endif
         endif
         if (.not.testAllgather(rank, size)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_ALLGATHER test failed'
            endif
         endif
         if (.not.testAlltoall(rank, size)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_ALLTOALL test failed'
            endif
         endif
         if (.not.testGatherv(rank, size, summation(size))) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_GATHERV failed'
            endif
         endif
         if (.not.testScatterv(rank, size, summation(size))) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_SCATTERV test failed'
            endif
         endif
         if (.not.testAllgatherv(rank, size, summation(size))) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_ALLGATHERV test failed'
            endif
         endif
         if (.not.testAlltoallv(rank, size, summation(size))) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_ALLTOALLV test failed'
            endif
         endif
         if (.not.testReduce_scatter(rank, size)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_REDUCE_SCATTER test failed'
            endif
         endif
         if (.not.testComm_create(rank, size)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_COMM_CREATE test failed'
            endif
         endif
         if (.not.testComm_dup(rank)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_COMM_DUP test failed'
            endif
         endif
         if (.not.testComm_split(rank)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_COMM_SPLIT test failed'
            endif
         endif
         if (.not.testComm_free(rank)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_COMM_FREE test failed'
            endif
         endif
         if (rank.le.2.and..not.testPcontrol(rank)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_PCONTROL test failed'
            endif
         endif
         if (rank.eq.0.and..not.testSend()) then
            result = .false.
            write(*,*) 'MPI_SEND test failed'
         endif
         if (rank.eq.1.and..not.testRecv()) then
            result = .false.
            write(*,*) 'MPI_RECV test failed'
         endif
         call MPI_PACK_SIZE(64, MPI_BYTE, MPI_COMM_WORLD, buffSize, 
     +                      ierr)
         buffSize = buffSize + MPI_BSEND_OVERHEAD
         if ((rank.eq.0.or.rank.eq.1).and..not.testBsend(rank, 
     +                                                   buffSize)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_BSEND test failed'
            endif
         endif
         if ((rank.eq.0.or.rank.eq.1).and..not.testRsend(rank)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_RSEND test failed'
            endif
         endif
         if (.not.testSsend(rank)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_SSEND test failed'
            endif
         endif
         if (rank.eq.0.and..not.testIsend()) then
            result = .false.
            write(*,*) 'MPI_ISEND test failed'
         endif
         if (rank.eq.1.and..not.testIrecv()) then
            result = .false.
            write(*,*) 'MPI_IRECV test failed'
         endif
         call MPI_PACK_SIZE(64, MPI_BYTE, MPI_COMM_WORLD, buffSize, 
     +                      ierr)
         buffSize = buffSize + MPI_BSEND_OVERHEAD
         if ((rank.eq.0.or.rank.eq.1).and..not.testIbsend(rank, 
     +                                                   buffSize)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_IBSEND test failed'
            endif
         endif
         if (.not.testIssend(rank)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_ISSEND test failed'
            endif
         endif
         if ((rank.eq.0.or.rank.eq.1).and..not.testIrsend(rank)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_IRSEND test failed'
            endif
         endif
         if ((rank.eq.0.or.rank.eq.1).and..not.testStart(rank)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_START test failed'
            endif
         endif
         if ((rank.eq.0.or.rank.eq.1).and..not.testWait(rank)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_WAIT test failed'
            endif
         endif
         if ((rank.eq.0.or.rank.eq.1).and..not.testStartall(rank)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_STARTALL test failed'
            endif
         endif
         if (.not.testWaitall(rank, size)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_WAITALL test failed'
            endif
         endif
         if (.not.testWaitany(rank, size)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_WAITANY test failed'
            endif
         endif
         if (.not.testWaitsome(rank, size)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_WAITSOME test failed'
            endif
         endif
         if ((rank.eq.0.or.rank.eq.1).and..not.testSendrecv(rank)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_SENDRECV test failed'
            endif
         endif
         if (rank.eq.0.and..not.testSend_init()) then
            result = .false.
            write(*,*) 'MPI_SEND_INIT test failed'
         endif
         if (rank.eq.1.and..not.testRecv_init()) then
            result = .false.
            write(*,*) 'MPI_RECV_INIT test failed'
         endif
c        Buffer size must be large enough to hold all messages
         call MPI_PACK_SIZE(64 * REPS, MPI_BYTE, MPI_COMM_WORLD,  
     +                      buffSize, ierr)
         buffSize = buffSize + MPI_BSEND_OVERHEAD
         if ((rank.eq.0.or.rank.eq.1).and..not.testBsend_init(rank, 
     +                                                   buffSize)) then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_BSEND_INIT test failed'
            endif
         endif
         if ((rank.eq.0.or.rank.eq.1).and..not.testRsend_init(rank)) 
     +      then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_RSEND_INIT test failed'
            endif
         endif
         if ((rank.eq.0.or.rank.eq.1).and..not.testSsend_init(rank)) 
     +      then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_SSEND_INIT test failed'
            endif
         endif
         if ((rank.eq.0.or.rank.eq.1).and..not.testRequest_free(rank)) 
     +      then
            result = .false.
            if (rank.eq.0) then
               write(*,*) 'MPI_REQUEST_FREE test failed'
            endif
         endif

c        Gather test results from all processes to process 1
         call MPI_GATHER(result, 1, MPI_LOGICAL, recvBuff, 1, 
     +                   MPI_LOGICAL, 1, MPI_COMM_WORLD, ierr);
         do i = 1, size
            if (rank.eq.1.and.recvBuff(i).neqv..true.) then
               result = .false.
            endif
         enddo
 99      testMPI = result
      end
