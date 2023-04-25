__kernel void minimum_index(__global float *distances, __global int *outputs, uint num_per, uint total, int numClusters) {
    int block = get_global_id(0);

    int start = block * num_per;
    int end = min(start + num_per, total);

    for (int idx=start; idx < end; idx++) {
        int distanceIdx = idx * numClusters;

        int lowest = 0;
        for (int i=1; i<numClusters; i++) {
            if (distances[distanceIdx+lowest] > distances[distanceIdx+i]) {
                lowest = i;
            }
        }
        outputs[idx] = lowest;
    }
}