Tests have Minecraft's registries and assets but no baked models. `GameIndexTest` and `TestIndex` build entries from texture names. Check real models with `/chroma debug <block id>` in a dev client.

`TestIndex.loadGame` binds vanilla block tags because they otherwise match nothing. `GameIndexTest` loads grass colours because the client resource reload that normally loads them never runs.
