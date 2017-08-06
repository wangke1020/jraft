# jraft

[![Build Status](https://travis-ci.org/wangke1020/jraft.svg?branch=master)](https://travis-ci.org/wangke1020/jraft)
[![Coverage Status](https://coveralls.io/repos/github/wangke1020/jraft/badge.svg)](https://coveralls.io/github/wangke1020/jraft)

[Raft](https://raft.github.io/raft.pdf) implementaion

#### Dependency:
- [grpc](http://www.grpc.io/)
- [leveldb](http://leveldb.org/)

#### Task:
- [x] basic protocol implementation
- [x] persistence of node state and log 
- [x] kv fsm for simple kv operation
- [ ] more tests
- [ ] log compaction
- [ ] snapshot
- [ ] cluster membership changes
- [ ] client utils for kv operation

