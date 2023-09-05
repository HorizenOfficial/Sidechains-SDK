# Release version - EON 1.0.0

---
## Features

### Force connecting to known peers
New configuration option `onlyConnectToKnownPeers` was added to `network` section of configuration file. When set to `true` it will force node to only try connecting to known peers. This option is disabled by default.
```
sparkz {
  network {
    onlyConnectToKnownPeers = false
  }
}
```

### Forgers Network
This feature adds a dedicated connection pool reserved for connecting to forger nodes. The size of the dedicated connection pool is configured by `maxForgerConnections` option in `network` section of configuration file. The default value is 20. This limit is independent of 

Configuration option `isForgerNode` in the `network` section has to be set to `true` to indicate that the node is a forger and other nodes should prioritize connecting with it. Default value is `false`.

Other nodes is the network will prioritize connecting to and broadcasting blocks to forger nodes.

```
sparkz {
  network {
    maxForgerConnections = 20
    isForgerNode = false
  }
}
```

The intent of this feature is to ensure 1-hop connectivity between forgers and to minimize block propagation time between forgers.


---
## Bug Fixes
- Synchronization race condition fix [SDK-1408]
---
## Improvements
- Block synchronization performance improvements [SDK-1262]:
    - new block lookup strategy based on the parent block id [SDK-1263]
    - preserve ordering of block during synchronization [SDK-1264]
    - history comparison logic rework [SDK-1267]
    - other performance improvements [SDK-1265, SDK-1266, SDK-1268]