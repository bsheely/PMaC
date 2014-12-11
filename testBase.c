/*
Copyright (c) 2010, The Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are
permitted provided that the following conditions are met:

*  Redistributions of source code must retain the above copyright notice, this list of conditions
    and the following disclaimer.
*  Redistributions in binary form must reproduce the above copyright notice, this list of conditions
    and the following disclaimer in the documentation and/or other materials provided with the distribution.
*  Neither the name of the Regents of the University of California nor the names of its contributors may be
    used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

#include <stdio.h>
#include <mpi.h>
#include <math.h>
#include "testBase.h"

static char MSG1[] = "Send 64 byte message from process 0 to process 1 with tag 1";
static char MSG2[] = "Send 100k message from process 0 to process 1 with tag 2";
static char MSG3[] = "Send 256k message from process 0 to process 1 with tag 3";
static char MSG4[] = "Isend 64 byte message from process 0 to process 1 with tag 4";
static char MSG5[] = "Call to Isend has returned - tag 5";
static char MSG6[] = "This message will never be sent - tag 6"; 
static char MSG7[] = "Send 64 byte message from process 0 to process 1 with tag 7";
static int NUM_REPS = 50;

int summation(int num) {	
   int temp;
   if (num == 0)
      return 0; 
   if (num == 1) 
      return 1;
   temp = num + summation(num - 1);
   return temp; 
}

/* After calling testAlltoallv, each processor has an array whose values have the pattern [a,1b,1c,2b,2c,2d,3b,3c,3d,3e,...]
   The following 2 functions are used to compute the expected value for any given index so the results can be verified
   Rank 0 = [0,100,101,200,201,202,300,301,302,303]
   Rank 1 = [1,102,103,203,204,205,304,305,306,307]
   Rank 2 = [2,104,105,206,207,208,308,309,310,311]
*/

double computeBase(int index) {
   int i, count = 0, counter = 0;
   double a[index + 1], value = 0;
   for (i = 0; i <= index; ++i) { 
      a[i] = value;
      if (count == counter) {
         counter += 1;
         count = 0;
         ++value;
      }
      else
         ++count; 
   }
   return a[index];
}

double computeValue(int rank, int index) {
   int i, count = 0, counter = 0;
   double a[index + 1], value = rank;
   for (i = 0; i <= index; ++i) { 
      a[i] = value;
      if (count == counter) {
         counter += 1;
         count = 0;
         value = rank * (counter + 1);
      }
      else {
         ++count;
         ++value; 
      }
   }
   return a[index];
}

/********* MPICollect ***********/

pmacbool_t testBarrier(int rank) {
   if (rank == 1)
      sleep(1);
   MPI_Barrier(MPI_COMM_WORLD);
   double p1, p2;
   int tag = 10;
   if (rank == 0) {
      p1 = MPI_Wtime();
      MPI_Send(&p1, 1, MPI_DOUBLE, 1, tag, MPI_COMM_WORLD);
   }
   if (rank == 1) {
      p2 = MPI_Wtime();
      MPI_Status status;         
      MPI_Recv(&p1, 1, MPI_DOUBLE, 0, tag, MPI_COMM_WORLD, &status);
      if (abs(p1 - p2) >= 0.25)
         return pmacFalse;
   }
   return pmacTrue;
}

pmacbool_t testBcast(int rank) {
   char MSG[] = "Bcast 64 byte message from process 0 to all other processes";
   char buffer[64] = "";
   if (rank == 0) {
      strcpy(buffer, MSG);
      MPI_Bcast(buffer, 64, MPI_BYTE, 0, MPI_COMM_WORLD);
   }
   else { 
      MPI_Bcast(buffer, 64, MPI_BYTE, 0, MPI_COMM_WORLD);
      if (strcmp(buffer, MSG) != 0)
         return pmacFalse;
   }
   return pmacTrue;
}

pmacbool_t testReduce(int rank, int numProcs) {
   double send = rank * 10, recv = 0;
   MPI_Reduce(&send, &recv, 1, MPI_DOUBLE, MPI_SUM, 0, MPI_COMM_WORLD);
   double expected;
   int i;
   for (i = 0; i < numProcs; ++i)
      expected += i * 10;
   if ((rank == 0 && recv != expected) || (rank != 0 && recv != 0))
      return pmacFalse;
   return pmacTrue;
}

pmacbool_t testScan(int rank, int numProcs) { 
   double send = rank, recv = 0;
   MPI_Scan (&send, &recv, 1, MPI_DOUBLE, MPI_SUM, MPI_COMM_WORLD);
   if (recv != summation(rank))
      return pmacFalse;
   return pmacTrue;
}

pmacbool_t testAllreduce(int rank, int numProcs) { 
   double send = rank * 10, recv;
   MPI_Allreduce(&send, &recv, 1, MPI_DOUBLE, MPI_SUM, MPI_COMM_WORLD);
   double expected;
   int i;
   for (i = 0; i < numProcs; ++i)
      expected += i * 10;
   if (recv != expected)
      return pmacFalse;
   return pmacTrue;
}

pmacbool_t testGather(int rank, int numProcs) { 
   double recvBuff[numProcs];
   double send = rank * 10;
   int i;
   for (i = 0; i < numProcs; ++i)
      recvBuff[i] = 0;
   MPI_Gather(&send, 1, MPI_DOUBLE, &recvBuff, 1, MPI_DOUBLE, 0, MPI_COMM_WORLD);
   for (i = 0; i < numProcs; ++i)
      if ((rank == 0 && recvBuff[i] != i * 10) || (rank != 0 && recvBuff[i] != 0))
         return pmacFalse;
   return pmacTrue;
}

pmacbool_t testScatter(int rank, int numProcs) { 
   double sendBuff[numProcs];
   double recv;
   int i;
   if (rank == 0)
      for (i = 0; i < numProcs; ++i)
         sendBuff[i] = i * 10;  
   MPI_Scatter(&sendBuff, 1, MPI_DOUBLE, &recv, 1, MPI_DOUBLE, 0, MPI_COMM_WORLD);
   if (recv != rank * 10)
      return pmacFalse;
   return pmacTrue;
}

pmacbool_t testAllgather(int rank, int numProcs) { 
   double recvBuff[numProcs];
   double send = rank * 10;
   MPI_Allgather(&send, 1, MPI_DOUBLE, &recvBuff, 1, MPI_DOUBLE, MPI_COMM_WORLD);
   int i;
   for (i = 0; i < numProcs; ++i)
      if (recvBuff[i] != i * 10)
         return pmacFalse;
   return pmacTrue;
}

pmacbool_t testAlltoall(int rank, int numProcs) { 
   double recvBuff[numProcs];
   double sendBuff[numProcs];
   int i;
   for (i = 0; i < numProcs; ++i)
      sendBuff[i] = rank * 10 + i;
   MPI_Alltoall(&sendBuff, 1, MPI_DOUBLE, &recvBuff, 1, MPI_DOUBLE, MPI_COMM_WORLD);
   for (i = 0; i < numProcs; ++i)
      if (recvBuff[i] != i * 10 + rank)
         return pmacFalse;
   return pmacTrue;
}

pmacbool_t testGatherv(int rank, int numProcs) { 
   int SIZE = summation(numProcs); 
   double recvBuff[SIZE];
   int i, j;
   for (i = 0; i < SIZE; ++i)
     recvBuff[i] = 0;
   double sendBuff[rank + 1];
   int recvCounts[numProcs];
   int displs[numProcs];
   for (i = 0; i < numProcs; ++i) {
      recvCounts[i] = i + 1;
      displs[i] = summation(i);
   }
   for (i = 0; i <= rank; ++i)
      sendBuff[i] = (10 * (rank + 1)) + i;
   MPI_Gatherv(&sendBuff, rank + 1, MPI_DOUBLE, recvBuff, recvCounts, displs, MPI_DOUBLE, 0, MPI_COMM_WORLD); 
   for (i = 0, j = 1; i < SIZE; ++i, ++j) {
      double expected = (int)recvBuff[i]%(10 * j) == 0 ? 10 * (i + 1) : (int)recvBuff[i]%(10 * j); 
      if ((rank == 0 && recvBuff[i] != expected) || (rank != 0 && recvBuff[i] != 0))
         return pmacFalse; 
   }
   return pmacTrue;
}

pmacbool_t testScatterv(int rank, int numProcs) { 
   int SIZE = summation(numProcs);
   double sendBuff[SIZE];
   int i;
   double recvBuff[rank + 1];
   int sendCounts[numProcs];
   int displs[numProcs];
   for (i = 0; i < numProcs; ++i) {
      sendCounts[i] = i + 1;
      displs[i] = summation(i);
   }
   if (rank == 0)
      for (i = 0; i < SIZE; ++i)
         sendBuff[i] = 10 * (i + 1); 
   MPI_Scatterv(&sendBuff, sendCounts, displs, MPI_DOUBLE, recvBuff, rank + 1, MPI_DOUBLE, 0, MPI_COMM_WORLD); 
   for (i = 0; i < rank + 1; ++i) 
      if (recvBuff[i] != summation(rank) * 10 + 10 * (i + 1))
         return pmacFalse;
   return pmacTrue;
}

pmacbool_t testAllgatherv(int rank, int numProcs) { 
   int SIZE = summation(numProcs); 
   double recvBuff[SIZE];
   int i, j;
   double sendBuff[rank + 1];
   int recvCounts[numProcs];
   int displs[numProcs];
   for (i = 0; i < numProcs; ++i) {
      recvCounts[i] = i + 1;
      displs[i] = summation(i);
   }
   for (i = 0; i <= rank; ++i)
      sendBuff[i] = (10 * (rank + 1)) + i;
   MPI_Allgatherv(&sendBuff, rank + 1, MPI_DOUBLE, recvBuff, recvCounts, displs, MPI_DOUBLE, MPI_COMM_WORLD); 
   for (i = 0, j = 1; i < SIZE; ++i, ++j) {
      double expected = (int)recvBuff[i]%(10 * j) == 0 ? 10 * (i + 1) : (int)recvBuff[i]%(10 * j); 
      if (recvBuff[i] != expected)
         return pmacFalse; 
   }
   return pmacTrue;
}

pmacbool_t testAlltoallv(int rank, int numProcs) { 
   int SIZE = (rank + 1) * numProcs;
   double sendBuff[SIZE];
   double recvBuff[summation(numProcs)];
   int sendCounts[numProcs];
   int recvCounts[numProcs];
   int sDispls[numProcs];
   int rDispls[numProcs];
   int i;
   for (i = 0; i < SIZE; ++i) 
      sendBuff[i] = rank * 100 + i; 
   for (i = 0; i < numProcs; ++i) {
      sendCounts[i] = rank + 1;
      recvCounts[i] = i + 1;
      sDispls[i] = (rank + 1) * i;
      rDispls[i] = summation(i);
   }
   MPI_Alltoallv(&sendBuff, &sendCounts, &sDispls, MPI_DOUBLE, &recvBuff, &recvCounts, &rDispls, MPI_DOUBLE, MPI_COMM_WORLD);
   for (i = 0; i < summation(numProcs); ++i) 
      if (recvBuff[i] != 100 * computeBase(i) + computeValue(rank, i))
         return pmacFalse;                                                                                                                                   
   return pmacTrue;
}

pmacbool_t testReduce_scatter(int rank, int numProcs) { 
   double sendBuff[numProcs];
   double recv;
   int recvCounts[numProcs], i;
   for (i = 0; i < numProcs; ++i) {
      sendBuff[i] = numProcs * rank + i;
      recvCounts[i] = 1;
   }   
   MPI_Reduce_scatter(sendBuff, &recv, recvCounts, MPI_DOUBLE, MPI_SUM, MPI_COMM_WORLD);
   double expected = 0;
   for (i = 0; i < numProcs; ++i) 
      expected += i * numProcs + rank;
   if (recv != expected)
      return pmacFalse;
   return pmacTrue;
}

/********* MPIComtor ***********/

pmacbool_t testComm_create(int rank, int size) {
   pmacbool_t result = pmacTrue;  
   MPI_Group worldCommGroup, newGroup;
   MPI_Comm comm;
   /* MPI_Comm_create must be created with an existing communicator 
      and must be called by every process in that communicator */
   int ranks1[2] = {0,1};
   int ranks2[size - 2], i;
   for (i = 2; i < size; ++i)
      ranks2[i - 2] = i;
   MPI_Comm_group(MPI_COMM_WORLD, &worldCommGroup);
   if (rank == 0 || rank == 1)
      MPI_Group_incl(worldCommGroup, 2, ranks1, &newGroup);
   else
      MPI_Group_incl(worldCommGroup, size - 2, ranks2, &newGroup);
   MPI_Comm_create(MPI_COMM_WORLD, newGroup, &comm);
   char MSG[] = "Bcast message from process 0 to all process on new comm";
   char buffer[64] = "";
   if (rank == 0) {
      strcpy(buffer, MSG);
      MPI_Bcast(buffer, 64, MPI_BYTE, 0, comm);
   }
   else {
      MPI_Bcast(buffer, 64, MPI_BYTE, 0, comm);
      if (rank == 1 && strcmp(buffer, MSG) != 0) 
         result = pmacFalse;
      if (rank != 1 && strcmp(buffer, "") != 0) 
         result = pmacFalse;
   }  
   MPI_Comm_free(&comm);
   return result;
}

pmacbool_t testComm_dup(int rank) { 
   pmacbool_t result = pmacTrue;  
   char MSG[] = "Bcast message from process 0 to all processes on dup comm";
   MPI_Comm dupCommWorld;
   MPI_Comm_dup(MPI_COMM_WORLD, &dupCommWorld);
   char buffer[64] = "";
   if (rank == 0) {
      strcpy(buffer, MSG);
      MPI_Bcast(buffer, 64, MPI_BYTE, 0, dupCommWorld);
   }
   else { 
      MPI_Bcast(buffer, 64, MPI_BYTE, 0, dupCommWorld);
      if (strcmp(buffer, MSG) != 0)
         result = pmacFalse;
   }
   MPI_Comm_free(&dupCommWorld);
   return result;
}

pmacbool_t testComm_split(int rank) {
   pmacbool_t result = pmacTrue;  
   int color = rank % 2 == 0 ? 2 : 1;   
   MPI_Comm comm;      
   MPI_Comm_split(MPI_COMM_WORLD, color, rank, &comm);
   char MSG[] = "Bcast message from process 0 to all processes on split comm";
   char buffer[64] = "";
   if (rank == 0 || rank == 1) {
      strcpy(buffer, MSG);
      MPI_Bcast(buffer, 64, MPI_BYTE, 0, comm);
   }
   else {
      MPI_Bcast(buffer, 64, MPI_BYTE, 0, comm);
      if (strcmp(buffer, MSG) != 0)
         result = pmacFalse;
   }
   MPI_Comm_free(&comm); 
   return result;
}

pmacbool_t testComm_free(int rank) {
   pmacbool_t result = pmacTrue;   
   char MSG[] = "Send message from process 0 to process 1 on dup comm";
   int tag = 20;
   MPI_Comm dupCommWorld;
   MPI_Comm_dup(MPI_COMM_WORLD, &dupCommWorld);
   char buffer[64] = "";
   if (rank == 0) {
      strcpy(buffer, MSG);
      MPI_Send(buffer, 64, MPI_BYTE, 1, tag, dupCommWorld);
   }
   else if (rank == 1) { 
      MPI_Status status;
      MPI_Recv(buffer, 64, MPI_BYTE, 0, tag, dupCommWorld, &status);
      if (strcmp(buffer, MSG) != 0)
         result = pmacFalse;
   }
   if (dupCommWorld == MPI_COMM_NULL)
      return pmacFalse; 
   MPI_Comm_free(&dupCommWorld);
   if (dupCommWorld != MPI_COMM_NULL)
      return pmacFalse; 
   return result;
}

/********* MPIMarker ***********/

pmacbool_t testPcontrol(int rank) {
   int level; 
   if (rank == 0)
      level = 0;
   else if (rank == 1)
      level = 1;
   else if (rank == 2)
      level = 2;
   /* All other values of level have profile library 
      defined effects and additional arguments */
   if (MPI_Pcontrol(level) != MPI_SUCCESS)
      return pmacFalse;
   return pmacTrue;
}

/********* MPIP2PComm ***********/

pmacbool_t testSend() { 
   char sendBuff1[64] = "";
   char sendBuff2[100*1024] = "";
   char sendBuff3[256*1024] = "";
   strcpy(sendBuff1, MSG1);
   strcpy(sendBuff2, MSG2);
   strcpy(sendBuff3, MSG3);
   MPI_Send(sendBuff1, 64, MPI_BYTE, 1, 1, MPI_COMM_WORLD);
   MPI_Send(sendBuff2, 100*1024, MPI_BYTE, 1, 2, MPI_COMM_WORLD);
   MPI_Send(sendBuff3, 256*1024, MPI_BYTE, 1, 3, MPI_COMM_WORLD);
   return pmacTrue;
}

pmacbool_t testBsend(int rank) {
   pmacbool_t result = pmacTrue;   
   char MSG1[] = "Bsend 64 byte message from process 0 to process 1";
   char MSG2[] = "Bsend has returned and buffer has been modified";
   int tag = 30;
   if (rank == 0) {
      char sendBuff[64] = ""; 
      int size;
      MPI_Pack_size(64, MPI_BYTE, MPI_COMM_WORLD, &size); 
      size += MPI_BSEND_OVERHEAD;
      char buffer[size];
      if (MPI_Buffer_attach(buffer, size) != MPI_SUCCESS)
         result = pmacFalse;
      strcpy(sendBuff, MSG1);
      MPI_Bsend(sendBuff, 64, MPI_BYTE, 1, tag, MPI_COMM_WORLD);
      /* Test that the sendbuffer can be immediately used again */ 
      strcpy(sendBuff, MSG2);
      MPI_Rsend(sendBuff, 64, MPI_BYTE, 1, tag + 1, MPI_COMM_WORLD);   
      MPI_Buffer_detach(buffer, &size);
   }
   else if (rank == 1) { 
      char recvBuff[64] = "";
      MPI_Status status;
      MPI_Recv(recvBuff, 64, MPI_BYTE, 0, tag + 1, MPI_COMM_WORLD, &status);
      if (strcmp(recvBuff, MSG2) != 0)
         result = pmacFalse;
      MPI_Recv(recvBuff, 64, MPI_BYTE, 0, tag, MPI_COMM_WORLD, &status);
      if (strcmp(recvBuff, MSG1) != 0)
         result = pmacFalse;
   }
   return result;
}

pmacbool_t testRsend(int rank) { 
   pmacbool_t result = pmacTrue;   
   char MSG[] = "Rsend 64 byte message from process 0 to process 1";
   int tag = 40;
   if (rank == 0) {
      char sendBuff[64] = "";
      strcpy(sendBuff, MSG);
      MPI_Rsend(sendBuff, 64, MPI_BYTE, 1, tag, MPI_COMM_WORLD);
      /* Test that Rsend returns before the receive is initiated */
      double t0 = MPI_Wtime();
      MPI_Send(&t0, 1, MPI_DOUBLE, 1, tag + 1, MPI_COMM_WORLD);
   }
   else if (rank == 1) { 
      sleep(1);
      char recvBuff[64] = "";
      MPI_Status status;
      double t0, t1 = MPI_Wtime();
      MPI_Recv(recvBuff, 64, MPI_BYTE, 0, tag, MPI_COMM_WORLD, &status);
      if (strcmp(recvBuff, MSG) != 0)
         result = pmacFalse;        
      MPI_Recv(&t0, 1, MPI_DOUBLE, 0, tag + 1, MPI_COMM_WORLD, &status);
      if (t1 <= t0)
         result = pmacFalse;
   }
   return result;
}

pmacbool_t testSsend(int rank) { 
   MPI_Barrier(MPI_COMM_WORLD);
   char MSG[] = "Ssend 64 byte message from process 0 to process 1";
   int tag = 50;
   if (rank == 0) {
      char sendBuff[64] = "";
      strcpy(sendBuff, MSG);
      MPI_Ssend(sendBuff, 64, MPI_BYTE, 1, tag, MPI_COMM_WORLD);
      /* Test that Ssend returns after the receive is initiated */
      double t0 = MPI_Wtime();
      MPI_Send(&t0, 1, MPI_DOUBLE, 1, tag + 1, MPI_COMM_WORLD);
   }
   else if (rank == 1) {  
      char recvBuff[64] = "";
      MPI_Status status;
      sleep(1);
      double t1a = MPI_Wtime();
      MPI_Irecv(recvBuff, 64, MPI_BYTE, 0, tag, MPI_COMM_WORLD, &status);
      double t1b = MPI_Wtime(), t0;
      MPI_Recv(&t0, 1, MPI_DOUBLE, 0, tag + 1, MPI_COMM_WORLD, &status);
      if (t0 < t1a || abs(t1b - t0) > 0.25)
         return pmacFalse;
      if (strcmp(recvBuff, MSG) != 0)
         return pmacFalse;
   }
   return pmacTrue;
}

pmacbool_t testRecv() { 
   char recvBuff1[64] = "";
   char recvBuff2[100*1024] = "";
   char recvBuff3[256*1024] = "";
   MPI_Status status;
   MPI_Recv(recvBuff1, 64, MPI_BYTE, 0, 1, MPI_COMM_WORLD, &status);
   MPI_Recv(recvBuff2, 100*1024, MPI_BYTE, 0, 2, MPI_COMM_WORLD, &status);
   MPI_Recv(recvBuff3, 256*1024, MPI_BYTE, 0, 3, MPI_COMM_WORLD, &status);
   if (strcmp(recvBuff1, MSG1) != 0 || strcmp(recvBuff2, MSG2) != 0 || 
       strcmp(recvBuff3, MSG3) != 0)
      return pmacFalse;
   return pmacTrue;
}

pmacbool_t testIsend() { 
   char sendBuff[64] = "";
   strcpy(sendBuff, MSG4);
   MPI_Request request;
   MPI_Isend(sendBuff, 64, MPI_BYTE, 1, 4, MPI_COMM_WORLD, &request);
   /* Test that Isend is non-blocking */
   char sendBuff2[64] = "";
   strcpy(sendBuff2, MSG5);
   MPI_Send(sendBuff2, 64, MPI_BYTE, 1, 5, MPI_COMM_WORLD);
   return pmacTrue;
}

pmacbool_t testIbsend(int rank) {
   pmacbool_t result = pmacTrue;   
   char MSG1[] = "Ibsend 64 byte message from process 0 to process 1";
   char MSG2[] = "Ibsend has returned and buffer has been modified";
   int tag = 60;
   if (rank == 0) {
      char sendBuff[64] = ""; 
      int size;
      MPI_Pack_size(64, MPI_BYTE, MPI_COMM_WORLD, &size);
      size += MPI_BSEND_OVERHEAD;
      char buffer[size];
      if (MPI_Buffer_attach(buffer, size) != MPI_SUCCESS)
         result = pmacFalse;
      strcpy(sendBuff, MSG1);
      MPI_Request request;
      MPI_Ibsend(sendBuff, 64, MPI_BYTE, 1, tag, MPI_COMM_WORLD, &request);
      /* Test that the sendbuffer can be immediately used again */
      strcpy(sendBuff, MSG2);
      MPI_Rsend(sendBuff, 64, MPI_BYTE, 1, tag + 1, MPI_COMM_WORLD);
      MPI_Buffer_detach(buffer, &size);
   }
   else if (rank == 1) { 
      char recvBuff[64] = "";
      MPI_Status status;
      MPI_Recv(recvBuff, 64, MPI_BYTE, 0, tag + 1, MPI_COMM_WORLD, &status);
      if (strcmp(recvBuff, MSG2) != 0)
         result = pmacFalse; 
      MPI_Recv(recvBuff, 64, MPI_BYTE, 0, tag, MPI_COMM_WORLD, &status);
      if (strcmp(recvBuff, MSG1) != 0)
         result = pmacFalse;
   } 
   return result;
}

pmacbool_t testIssend(int rank) { 
   pmacbool_t result = pmacTrue;   
   MPI_Barrier(MPI_COMM_WORLD);
   char MSG1[] = "Issend 64 byte message from process 0 to process 1";
   char MSG2[] = "Call to Issend has returned";
   int tag = 70;
   MPI_Status status;
   if (rank == 0) { 
      char sendBuff[64] = "";
      strcpy(sendBuff, MSG1);
      MPI_Request request;
      MPI_Issend(sendBuff, 64, MPI_BYTE, 1, tag, MPI_COMM_WORLD, &request);
      /* Test that Issend is non-blocking */
      char sendBuff2[64] = "";
      strcpy(sendBuff2, MSG2);
      MPI_Send(sendBuff2, 64, MPI_BYTE, 1, tag + 1, MPI_COMM_WORLD);
   }
   else if (rank == 1) {  
      char recvBuff[64] = ""; 
      MPI_Recv(recvBuff, 64, MPI_BYTE, 0, tag + 1, MPI_COMM_WORLD, &status);
      if (strcmp(recvBuff, MSG2) != 0)
         result = pmacFalse; 
      MPI_Recv(recvBuff, 64, MPI_BYTE, 0, tag, MPI_COMM_WORLD, &status);
      if (strcmp(recvBuff, MSG1) != 0)
         result = pmacFalse; 
   }
   return result;
}

pmacbool_t testIrsend(int rank) { 
   pmacbool_t result = pmacTrue;   
   char MSG1[] = "Irsend 64 byte message from process 0 to process 1";
   char MSG2[] = "Receive message from process 1";
   int tag = 80;
   MPI_Status status;
   char sendBuff[64] = "";
   char recvBuff[64] = "";
   if (rank == 0) { 
      strcpy(sendBuff, MSG1);
      double t1;
      /* Test that Irsend returns before the receive is initiated */ 
      MPI_Recv(&t1, 1, MPI_DOUBLE, 1, tag, MPI_COMM_WORLD, &status);
      sleep(1);
      MPI_Request request;
      MPI_Irsend(sendBuff, 64, MPI_BYTE, 1, tag + 1, MPI_COMM_WORLD, &request);
      double t0 = MPI_Wtime();
      if (t1 >= t0)
         result = pmacFalse;
      /* Test that Irsend is non-blocking */ 
      MPI_Recv(recvBuff, 64, MPI_BYTE, 1, tag + 2, MPI_COMM_WORLD, &status);
      if (strcmp(recvBuff, MSG2) != 0)
         return pmacFalse;
   }
   else if (rank == 1) { 
      strcpy(sendBuff, MSG2);
      MPI_Send(sendBuff, 64, MPI_BYTE, 0, tag + 2, MPI_COMM_WORLD);
      double t1 = MPI_Wtime();
      MPI_Send(&t1, 1, MPI_DOUBLE, 0, tag, MPI_COMM_WORLD);  
      MPI_Recv(recvBuff, 64, MPI_BYTE, 0, tag + 1, MPI_COMM_WORLD, &status);
      if (strcmp(recvBuff, MSG1) != 0)
         return pmacFalse;
   }
   return result;
}

pmacbool_t testIrecv() {
   pmacbool_t result = pmacTrue;   
   char recvBuff[64] = "";
   MPI_Status status;
   /* Test that Irecv is non-blocking */
   MPI_Irecv(recvBuff, 64, MPI_BYTE, 0, 6, MPI_COMM_WORLD, &status);
   if (strcmp(recvBuff, "") != 0)
      result = pmacFalse; 
   MPI_Recv(recvBuff, 64, MPI_BYTE, 0, 5, MPI_COMM_WORLD, &status);
   if (strcmp(recvBuff, MSG5) != 0)
      result = pmacFalse; 
   MPI_Irecv(recvBuff, 64, MPI_BYTE, 0, 4, MPI_COMM_WORLD, &status);
   if (strcmp(recvBuff, MSG4) != 0)
      result = pmacFalse; 
   return result;
}

pmacbool_t testStart(int rank) { 
   pmacbool_t result = pmacTrue;   
   char MSG[] = "Send 64 byte message from process 0 to process 1"; 
   int tag = 90, i;
   MPI_Status status;
   if (rank == 0) {
      char sendBuff[64] = ""; 
      strcpy(sendBuff, MSG);
      MPI_Request request;
      MPI_Send_init(sendBuff, 64, MPI_BYTE, 1, tag, MPI_COMM_WORLD, &request);
      for (i = 0; i < NUM_REPS; ++i) {
         MPI_Start(&request);
         MPI_Wait(&request, &status);
      }
      MPI_Request_free(&request);
   }
   else if (rank == 1) {
      char recvBuff[64];
      for (i = 0; i < NUM_REPS; ++i) {
         MPI_Recv(recvBuff, 64, MPI_BYTE, 0, tag, MPI_COMM_WORLD, &status);
         if (strcmp(recvBuff, MSG) != 0)
            result = pmacFalse;   
      }
   }
   return result;
}

pmacbool_t testWait(int rank) {
   char MSG[] = "ISend 64 byte message from process 0 to process 1";
   int tag = 100;
   if (rank == 0) { 
      sleep(1);
      char sendBuff[64] = "";
      strcpy(sendBuff, MSG);
      MPI_Send(sendBuff, 64, MPI_BYTE, 1, tag, MPI_COMM_WORLD);     
   }
   else if (rank == 1) {
      char recvBuff[64] = "";
      MPI_Status status;
      MPI_Request request;
      MPI_Irecv(recvBuff, 64, MPI_BYTE, 0, tag, MPI_COMM_WORLD, &request);
      MPI_Wait(&request, &status);
      if (strcmp(recvBuff, MSG) != 0)
         return pmacFalse; 
   }
   return pmacTrue;
}

pmacbool_t testStartall(int rank) { 
   pmacbool_t result = pmacTrue;   
   char MSG1[] = "Send 64 byte message from process 0 to process 1 with tag 90"; 
   char MSG2[] = "Send 64 byte message from process 0 to process 1 with tag 91"; 
   int i;
   MPI_Status status;
   if (rank == 0) {
      MPI_Request request[2];
      char sendBuff1[64] = "";
      strcpy(sendBuff1, MSG1);
      MPI_Send_init(sendBuff1, 64, MPI_BYTE, 1, 90, MPI_COMM_WORLD, &request[0]); 
      char sendBuff2[64] = "";  
      strcpy(sendBuff2, MSG2);
      MPI_Send_init(sendBuff2, 64, MPI_BYTE, 1, 91, MPI_COMM_WORLD, &request[1]); 
      MPI_Status status[2];
      for (i = 0; i < NUM_REPS; ++i) {
         MPI_Startall(2, request);
         MPI_Waitall(2, request, status);
      }
      MPI_Request_free(&request[0]);
      MPI_Request_free(&request[1]);
   }
   else if (rank == 1) {
      char recvBuff1[64];
      char recvBuff2[64];
      MPI_Request request;
      for (i = 0; i < NUM_REPS; ++i) {
         MPI_Irecv(recvBuff1, 64, MPI_BYTE, 0, 90, MPI_COMM_WORLD, &request); 
         MPI_Recv(recvBuff2, 64, MPI_BYTE, 0, 91, MPI_COMM_WORLD, &status);
         if (strcmp(recvBuff2, MSG2) != 0)
            result = pmacFalse; 
         MPI_Wait(&request, &status);
         if (strcmp(recvBuff1, MSG1) != 0)
            result = pmacFalse; 
      }
   }
   return result;
}

pmacbool_t testWaitall(int rank, int size) {
   int tag = 120;
   if (rank == 0) { 
      char recvBuff[size - 1][64];
      int i;
      MPI_Request request[size - 1];
      MPI_Status status[size - 1];
      for (i = 1; i < size; ++i)
         MPI_Irecv(recvBuff[i - 1], 64, MPI_BYTE, i, tag + i, MPI_COMM_WORLD, &request[i - 1]);
      MPI_Waitall(size - 1, request, status);
      for (i = 1; i < size; ++i) {
         char buff[64] = "";
         sprintf(buff, "Send 64 byte message from process %d to process 0 with tag %d", i, tag + i);
         if (strcmp(recvBuff[i - 1], buff) != 0)
            return pmacFalse; 
      }
   }
   else {
      char sendBuff[64] = "";
      sprintf(sendBuff, "Send 64 byte message from process %d to process 0 with tag %d", rank, tag + rank);
      MPI_Send(sendBuff, 64, MPI_BYTE, 0, tag + rank, MPI_COMM_WORLD); 
   } 
   return pmacTrue;
}

pmacbool_t testWaitany(int rank, int size) { 
   int tag = 130;
   if (rank == 0) { 
      char recvBuff[size - 1][64];
      int i, index;
      MPI_Request request[size - 1];
      MPI_Status status[size - 1];
      for (i = 1; i < size; ++i) 
         MPI_Irecv(recvBuff[i - 1], 64, MPI_BYTE, i, tag + i, MPI_COMM_WORLD, &request[i - 1]);
      for (i = 1; i < size; ++i) 
         MPI_Waitany(size - 1, request, &index, status);
      char buff[64] = "";
      sprintf(buff, "Isend 64 byte message from process %d to process 0 with tag %d", index + 1, tag + index + 1);
      if (strcmp(recvBuff[index], buff) != 0)
         return pmacFalse; 
   }
   else {
      char sendBuff[64] = "";
      sprintf(sendBuff, "Isend 64 byte message from process %d to process 0 with tag %d", rank, tag + rank);
      MPI_Request request;
      MPI_Isend(sendBuff, 64, MPI_BYTE, 0, tag + rank, MPI_COMM_WORLD, &request); 
   } 
   return pmacTrue;
}

pmacbool_t testWaitsome(int rank, int size) {
   int tag = 140;
   if (rank == 0) { 
      char recvBuff[size - 1][64];
      int i, count, indices[size - 1], remaining = size - 1;
      MPI_Request request[size - 1];
      MPI_Status status[size - 1];
      for (i = 1; i < size; ++i) 
         MPI_Irecv(recvBuff[i - 1], 64, MPI_BYTE, i, tag + i, MPI_COMM_WORLD, &request[i - 1]);
      while (remaining > 0) {
         MPI_Waitsome(size - 1, request, &count, indices, status);
         if (count > 0) 
            remaining -= count;
      }
      for (i = 0; i < count; ++i) {
         int index = indices[i];
         char buff[64] = "";
         sprintf(buff, "Isend 64 byte message from process %d to process 0 with tag %d", index + 1, tag + index + 1);
         if (strcmp(recvBuff[index], buff) != 0)
            return pmacFalse; 
      }
   }
   else {
      char sendBuff[64] = "";
      sprintf(sendBuff, "Isend 64 byte message from process %d to process 0 with tag %d", rank, tag + rank);
      MPI_Request request;
      MPI_Isend(sendBuff, 64, MPI_BYTE, 0, tag + rank, MPI_COMM_WORLD, &request); 
   }  
   return pmacTrue;
}

pmacbool_t testSendrecv(int rank) { 
   char MSG1[] = "Send 64 byte message from process 0 to process 1 with tag 150";
   char MSG2[] = "Send 64 byte message from process 1 to process 0 with tag 151";
   char sendBuff[64] = "";
   int source, dest, sendTag, recvTag;
   if (rank == 0) {
      dest = 1;
      source = 1;
      sendTag = 150;
      recvTag = 151;
      strcpy(sendBuff, MSG1); 
   }
   else if (rank == 1) {
      dest = 0;
      source = 0;
      sendTag = 151;
      recvTag = 150;
      strcpy(sendBuff, MSG2); 
   } 
   char recvBuff[64] = "";
   MPI_Status status;
   MPI_Sendrecv(sendBuff, 64, MPI_BYTE, dest, sendTag, recvBuff, 64, MPI_BYTE, source, recvTag, MPI_COMM_WORLD, &status);
   if (rank == 0 && strcmp(recvBuff, MSG2) != 0)
      return pmacFalse;
   if (rank == 1 && strcmp(recvBuff, MSG1) != 0)
      return pmacFalse;
   return pmacTrue;
}

/********* MPIReqinit ***********/

pmacbool_t testSend_init() {
   MPI_Status status;
   char sendBuff[64] = "";
   strcpy(sendBuff, MSG7);
   MPI_Request request;
   MPI_Send_init(sendBuff, 64, MPI_BYTE, 1, 7, MPI_COMM_WORLD, &request);
   int i;
   for (i = 0; i < NUM_REPS; ++i) {
      MPI_Start(&request);
      MPI_Wait(&request, &status);
   }
   MPI_Request_free(&request); 
   return pmacTrue;
}

pmacbool_t testRecv_init() {
   pmacbool_t result = pmacTrue;   
   MPI_Request request;
   char recvBuff[64];
   MPI_Recv_init(recvBuff, 64, MPI_BYTE, 0, 7, MPI_COMM_WORLD, &request);
   MPI_Status status;
   int i;
   for (i = 0; i < NUM_REPS; ++i) {
      MPI_Start(&request);
      MPI_Wait(&request, &status);
      if (strcmp(recvBuff, MSG7) != 0)
         result = pmacFalse;
   }
   MPI_Request_free(&request); 
   return result; 
}

pmacbool_t testBsend_init(int rank) {
   pmacbool_t result = pmacTrue;   
   char MSG1[] = "Bsend 64 byte message from process 0 to process 1";
   char MSG2[] = "Bsend has started and buffer has been modified";
   int tag = 160, i;
   MPI_Status status;
   if (rank == 0) {
      char sendBuff[64] = ""; 
      int size;
      /* NOTE: Buffer size must be large enough to hold all messages */
      MPI_Pack_size(64 * NUM_REPS, MPI_BYTE, MPI_COMM_WORLD, &size);
      size += MPI_BSEND_OVERHEAD;
      char buffer[size];
      if (MPI_Buffer_attach(buffer, size) != MPI_SUCCESS)
         result = pmacFalse;
      MPI_Request request;
      MPI_Bsend_init(sendBuff, 64, MPI_BYTE, 1, tag, MPI_COMM_WORLD, &request);
      for (i = 0; i < NUM_REPS; ++i) {
         strcpy(sendBuff, MSG1);
         MPI_Start(&request);
         /* Test that the sendbuffer can be used again */         
         strcpy(sendBuff, MSG2);
         MPI_Rsend(sendBuff, 64, MPI_BYTE, 1, tag + 1, MPI_COMM_WORLD);
         MPI_Wait(&request, &status);
      }
      MPI_Buffer_detach(buffer, &size);
      MPI_Request_free(&request); 
   }
   else if (rank == 1) {
      char recvBuff[NUM_REPS][64];
      for (i = 0; i < NUM_REPS; ++i) {
         MPI_Recv(recvBuff[i], 64, MPI_BYTE, 0, tag + 1, MPI_COMM_WORLD, &status);
         if (strcmp(recvBuff[i], MSG2) != 0)
            result = pmacFalse; 
         MPI_Recv(recvBuff[i], 64, MPI_BYTE, 0, tag, MPI_COMM_WORLD, &status);
         if (strcmp(recvBuff[i], MSG1) != 0)
            result = pmacFalse; 
      }    
   }
   return result; 
}

pmacbool_t testRsend_init(int rank) {
   pmacbool_t result = pmacTrue;   
   char MSG[] = "Rsend 64 byte message from process 0 to process 1";
   int tag = 170, i;
   MPI_Status status;
   if (rank == 0) {
      char sendBuff[64] = ""; 
      strcpy(sendBuff, MSG);
      MPI_Request request;
      MPI_Rsend_init(sendBuff, 64, MPI_BYTE, 1, tag, MPI_COMM_WORLD, &request);
      for (i = 0; i < NUM_REPS; ++i) {
         MPI_Start(&request);
         /* Test that Rsend returns before the receive is initiated */ 
         double t0 = MPI_Wtime();
         MPI_Send(&t0, 1, MPI_DOUBLE, 1, tag + 1, MPI_COMM_WORLD);
         MPI_Wait(&request, &status);
      }
      MPI_Request_free(&request); 
   }
   else if (rank == 1) {
      char recvBuff[NUM_REPS][64];
      for (i = 0; i < NUM_REPS; ++i) {
         sleep(1);
         double t0, t1 = MPI_Wtime();
         MPI_Recv(recvBuff[i], 64, MPI_BYTE, 0, tag, MPI_COMM_WORLD, &status);
         if (strcmp(recvBuff[i], MSG) != 0)
            result = pmacFalse; 
         MPI_Recv(&t0, 1, MPI_DOUBLE, 0, tag + 1, MPI_COMM_WORLD, &status);
         if (t1 <= t0)
            result = pmacFalse;
      }    
   }
   return result; 

}

pmacbool_t testSsend_init(int rank) {
   pmacbool_t result = pmacTrue;
   char MSG[] = "Ssend 64 byte message from process 0 to process 1";
   int tag = 180, i;
   MPI_Status status;
   if (rank == 0) {
      char sendBuff[64] = ""; 
      strcpy(sendBuff, MSG);
      MPI_Request request;
      MPI_Ssend_init(sendBuff, 64, MPI_BYTE, 1, tag, MPI_COMM_WORLD, &request);
      for (i = 0; i < NUM_REPS; ++i) {
         MPI_Start(&request);
         MPI_Wait(&request, &status);
      }
      MPI_Request_free(&request); 
   }
   else if (rank == 1) {
      char recvBuff[NUM_REPS][64];
      for (i = 0; i < NUM_REPS; ++i) {
         MPI_Recv(recvBuff[i], 64, MPI_BYTE, 0, tag, MPI_COMM_WORLD, &status);
         if (strcmp(recvBuff[i], MSG) != 0)
            result = pmacFalse; 
      }    
   }
   return result;
}

pmacbool_t testRequest_free(int rank) { 
   int tag = 190, i;
   MPI_Status status;
   if (rank == 0) {
      char sendBuff[64] = "Send 64 byte message from process 0 to process 1"; 
      MPI_Request request;
      MPI_Send_init(sendBuff, 64, MPI_BYTE, 1, tag, MPI_COMM_WORLD, &request);
      for (i = 0; i < NUM_REPS; ++i) {
         MPI_Start(&request);
         MPI_Wait(&request, &status);
      }
      if (request == MPI_REQUEST_NULL)
         return pmacFalse; 
      MPI_Request_free(&request);
      if (request != MPI_REQUEST_NULL)
         return pmacFalse; 
   }
   else if (rank == 1) {
      char recvBuff[64];
      for (i = 0; i < NUM_REPS; ++i)
         MPI_Recv(recvBuff, 64, MPI_BYTE, 0, tag, MPI_COMM_WORLD, &status);  
   }
   return pmacTrue;
}

pmacbool_t testMPI(int rank, int size) { 
   pmacbool_t result = pmacTrue;
   if (size < 4) {
      if (rank == 0)
         printf("ERROR: At least 4 processes are required for MPI testing\n");
      return pmacFalse;
   }
   if (!testBarrier(rank)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Barrier test failed\n");
   }
   if (!testBcast(rank)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Bcast test failed\n");
   }
   if (!testReduce(rank, size)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Reduce test failed\n");
   }
   if (!testScan(rank, size)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Scan test failed\n");
   }
   if (!testAllreduce(rank, size)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Allreduce test failed\n");
   }
   if (!testGather(rank, size)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Gather test failed\n");
   }
   if (!testScatter(rank, size)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Scatter test failed\n");
   }
   if (!testAllgather(rank, size)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Allgather test failed\n");
   }
   if (!testAlltoall(rank, size)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Alltoall test failed\n");
   }
   if (!testGatherv(rank, size)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Gatherv test failed\n");
   }
   if (!testScatterv(rank, size)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Scatterv test failed\n");
   }
   if (!testAllgatherv(rank, size)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Allgatherv test failed\n");
   }
   if (!testAlltoallv(rank, size)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Alltoallv test failed\n");
   }
   if (!testReduce_scatter(rank, size)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Reduce_scatter test failed\n");
   }
   if (!testComm_create(rank, size)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Comm_create test failed\n");
   }
   if (!testComm_dup(rank)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Comm_dup test failed\n");
   }
   if (!testComm_split(rank)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Comm_split test failed\n");
   }
   if (!testComm_free(rank)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Comm_free test failed\n");
   }
   if (rank <= 2 && !testPcontrol(rank)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Pcontrol test failed\n");
   }
   if (rank == 0 && !testSend()) {
      result = pmacFalse;
      printf("MPI_Send test failed\n");
   }
   if (rank == 1 && !testRecv()) {
      result = pmacFalse;
      printf("MPI_Recv test failed\n");
   }
   if ((rank == 0 || rank == 1) && !testBsend(rank)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Bsend test failed\n");
   }
   if ((rank == 0 || rank == 1) && !testRsend(rank)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Rsend test failed\n");
   }
   if (!testSsend(rank)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Ssend test failed\n");
   }
   if (rank == 0 && !testIsend()) {
      result = pmacFalse;
      printf("MPI_Isend test failed\n");
   }
   if (rank == 1 && !testIrecv()) {
      result = pmacFalse;
      printf("MPI_Irecv test failed\n");
   }
   if ((rank == 0 || rank == 1) && !testIbsend(rank)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Ibsend test failed\n");
   }
   if (!testIssend(rank)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Issend test failed\n");
   }
   if ((rank == 0 || rank == 1) && !testIrsend(rank)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Irsend test failed\n");
   }
   if ((rank == 0 || rank == 1) && !testStart(rank)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Start test failed\n");
   }
   if ((rank == 0 || rank == 1) && !testWait(rank)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Wait test failed\n");
   }
   if ((rank == 0 || rank == 1) && !testStartall(rank)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Startall test failed\n");
   }
   if (!testWaitall(rank, size)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Waitall test failed\n");
   }
   if (!testWaitany(rank, size)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Waitany test failed\n");
   }
   if (!testWaitsome(rank, size)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Waitsome test failed\n");
   }
   if ((rank == 0 || rank == 1) && !testSendrecv(rank)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Sendrecv test failed\n");
   }
   if (rank == 0 && !testSend_init()) {
      result = pmacFalse;
      printf("MPI_Send_init test failed\n");
   }
   if (rank == 1 && !testRecv_init()) {
      result = pmacFalse;
      printf("MPI_Recv_init test failed\n");
   }
   if ((rank == 0 || rank == 1) &&!testBsend_init(rank)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Bsend_init test failed\n");
   }
   if ((rank == 0 || rank == 1) &&!testRsend_init(rank)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Rsend_init test failed\n");
   }
   if ((rank == 0 || rank == 1) &&!testSsend_init(rank)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Ssend_init test failed\n");
   }
   if ((rank == 0 || rank == 1) &&!testRequest_free(rank)) {
      result = pmacFalse;
      if (rank == 0)
         printf("MPI_Request_free test failed\n");
   }

   /* Gather test results from all processes to process 1 */
   int recvBuff[size], i;
   MPI_Gather(&result, 1, MPI_INT, &recvBuff, 1, MPI_INT, 1, MPI_COMM_WORLD);
   for (i = 0; i < size; ++i)
      if (rank == 1 && recvBuff[i] != pmacTrue)
         result = pmacFalse;
   return result;
}
