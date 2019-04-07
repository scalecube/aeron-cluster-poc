That's example of how to use aeron-cluster library to start cluster of several services and communicating with this cluster.

scripts/* contains all starting samples.

Running examples without VM options passed makes no sense since runners do not provide with necessary default values to start cluster.

How to run:

Run node-0.sh, node-1.sh, node-2.sh and observe that one of nodes becomes leader.
Now we have 3 nodes of Echo service running in aeron-cluster.

Then start client.sh

Observe client exchanging with 'hello' messages with running cluster.
Try stopping/restarting nodes and observe client anyways running.