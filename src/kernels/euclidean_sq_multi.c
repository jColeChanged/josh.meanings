#ifndef SIZE
#define SIZE 3
#endif

__kernel void euclidean_sq_distances(__global float *distances,
                                     __global const float *points,
                                     __global const float *centroids,
                                     uint num_per, uint total, int numClusters) {
    int block = get_global_id(0);
    int start = block * num_per;
    int end = min(start + num_per, total);

    for (int idx = start; idx < end; idx++) {
        for (int c = 0; c < numClusters; c++) {
            float sum = 0.0f;
            for (int i = 0; i < SIZE; i++) {
                float diff = points[idx * SIZE + i] - centroids[c * SIZE + i];
                sum += diff * diff;
            }
            distances[(idx * numClusters) + c] = sum;
        }
    }
}
