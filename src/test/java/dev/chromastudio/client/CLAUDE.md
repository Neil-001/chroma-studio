The client code's tests, which load Minecraft's registries and assets without a client or a window.

- No models are baked without a client, so `GameIndexTest` and `TestIndex` put entries together by hand from texture names. The dev client's `/chroma debug <block id>` covers what the models say.
- `TestIndex.loadGame` binds the vanilla block tags, as a world has them. Without them, a check such as whether a block is sand says no to every block.
- The client loads the grass colours on a resource reload, which doesn't happen in a test, so `GameIndexTest` loads them itself.
