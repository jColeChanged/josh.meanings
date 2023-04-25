#ifndef SIZE
#define SIZE 3
#endif

float wasserstein_distance(float *hole, __global const float *mound, int m) {

    float dist[SIZE] = {0};
    for (int i = 0; i < SIZE; i++) {
        dist[i] = hole[i] - mound[(m * SIZE) + i];
    }

    for (int i = 1; i < SIZE; i++) {
        dist[i] += dist[i - 1];
    }


    float sum = 0;
    for (int i = 0; i < SIZE; i++) {
        sum += fabs(dist[i]);
    }

    return sum;
}


__kernel void wasserstein_distances(__global float *distances, __global const float *holes, __global const float *mounds, uint num_per, uint total, int numClusters) {
    int block = get_global_id(0);

    int start = block * num_per;
    int end = min(start + num_per, total);

    for (int idx=start; idx < end; idx++) {
        int holeIndexStart = idx * SIZE;
        // Populate the hole array with the context of holes
        float hole[SIZE] = {0};
        for (int i=0; i < SIZE; i++) {
            hole[i] = holes[holeIndexStart + i];
        }

        for (int i=0; i < numClusters; i++) {
            distances[(idx * numClusters) + i] = wasserstein_distance(hole, mounds, i);
        }
    }
}


/*
float wasserstein_distance(float3 hole, __global const float3 *mound, int m) {

    float3 dist = hole - mound[m];

    for (int i = 1; i < SIZE; i++) {
        dist.s[i] += dist.s[i - 1];
    }

    return fabs(dist.s[0]) + fabs(dist.s[1]) + fabs(dist.s[2]);
}


__kernel void wasserstein_distances(__global float *distances, __global const float3 *holes, __global const float3 *mounds, uint num_per, uint total, int numClusters) {
    int block = get_global_id(0);

    int start = block * num_per;
    int end = min(start + num_per, total);

    for (int idx=start; idx < end; idx++) {
        float3 hole = holes[idx];

        for (int i=0; i < numClusters; i++) {
            distances[(idx * numClusters) + i] = wasserstein_distance(hole, mounds, i);
        }
    }
}
*/