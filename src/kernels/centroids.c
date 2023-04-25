#ifndef SIZE
#define SIZE 3
#endif

__kernel void sum_by_group(__global float* points, __global int* assignments, __global float* centroids, __global int* counts, int total) {
    int block = get_global_id(0);

    __local float localCentroids[SIZE];
    __local int localCounts;
    for (int f=0; f < SIZE; f++) {
        localCentroids[f] = 0;
    }
    localCounts = 0;

    for (int idx=0; idx < total; idx++) {
        float* features = points + idx*SIZE;
        int assignment = assignments[idx];
        if (assignment == block) {
            for (int f=0; f < SIZE; f++) {
                localCentroids[f] += features[f];
            }
            localCounts++;
        }
    }

    barrier(CLK_LOCAL_MEM_FENCE);

    if (get_local_id(0) == 0) {
        for (int f=0; f < SIZE; f++) {
            centroids[block * SIZE + f] += localCentroids[f];
        }
        counts[block] += localCounts;
    }
}