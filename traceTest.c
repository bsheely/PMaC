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
#include "Base.h"
#include "testBase.h"

void cleanup() {
   system("rm *.psins");
   system("rm *.bin");
   remove("RankPid");
   remove("RankPid.extended");
}

pmacbool_t verifyFiles(int taskCount) {
   char psinsFile[STRING_BUFFER_SIZE];
   char fileName[STRING_BUFFER_SIZE];
   char command[STRING_BUFFER_SIZE];
   int i;
   for (i = 0; i < taskCount; ++i) {
      sprintf(fileName, "psinstrace.rank%05d.tasks%05d.bin", i, taskCount);
      if (fopen(fileName, "w") == NULL)
         return pmacFalse;
   }
   sprintf(command, "./bin/mpi2psins --application traceTest --dataset standard --cpu_count %d --trace_dir . --ease_requests --dyn_constants", taskCount);
   system(command);
   sprintf(psinsFile, "traceTest_standard_%04d.psins", taskCount);
   if (access("RankPid", F_OK) == -1 || access("RankPid.extended", F_OK) == -1 || access(psinsFile, F_OK) == -1)
      return pmacFalse;
   return pmacTrue;
}

int main(int argc, char** argv) {
   int size, rank;
   MPI_Init(&argc, &argv);
   MPI_Comm_size(MPI_COMM_WORLD, &size);
   MPI_Comm_rank(MPI_COMM_WORLD, &rank);
   pmacbool_t success = testMPI(rank, size);
   MPI_Finalize();

   if (rank == 1) {
      if (success)
         success = verifyFiles(size);
      cleanup();
      if (success) {
         printf("PASSED\n");
         exit(0);
      }
      else {
         printf("FAILED\n");
         exit(1);
      }
   }
}
