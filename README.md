# SimpleCompression

A composable compression library with automatic probe based algorithm sequencing, manual pipeline control, and a universal decoder driven by self-describing headers.

This is a full parity feature rewrite of the Python library simple-compression [https://github.com/th3f0rk/simple-compression] in Java.
Same general architecture and the exact same API philosopy and similar binary format, just faster

---

## Why Java

The Python implementation was very, very slow. It only topped out at about 1 MB/s encode and 10 MB/s decode. The algorithms were implemented naively and unoptimized 
and python has a lot of natural overhead, especially with bitwise operations. This rewrite aims to completely fix the speed issue while also attempting to improve compression ratios.

**Speed After Rewrite:**
| Algorithm | Encode | Decode |
|---|---|---|
| RLE | 350-420 MB/s | 600-700 MB/s |
| LZ77 | 45-130 MB/s | 450-560 MB/s |
| Huffman | not implemented | not implemented |
| Delta | not implmeneted | not implemented |

---

## Planned Algorithms

For v1.0.0 release we plan to have full feature parity with the python implementation as well as adding in a Delta-Encoding algorithm. 
In the future we will consider adding algorithms such as ANS entropy coding.

---

## Current Roadmap

- implement Huffman with tests
- implement Delta with tests
- rewrite and adjust probe logic from python version
- implement main api layer with general decoder
- write documentation for each algorithm's format and byte spec
- Implement Canterbury corpus benchmarks
- Finalize tests and documentation
- Optimize where needed

---

## License 

MIT
