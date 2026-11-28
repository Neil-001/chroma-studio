# Compile-only libraries

Axiom's client API and the ImGui binding its options panels use, which `dev.chromastudio.client.axiom` compiles against. Axiom supplies both at run time, so neither is in the built jars. They are kept here, in Maven's layout, so a build doesn't depend on JitPack, which builds these on request and can be down. Axiom's own jar is never here.

| Library | Source | Licence | SHA-256 |
|---|---|---|---|
| `com.github.Moulberry:AxiomClientAPI:v2.0.1` | [Moulberry/AxiomClientAPI](https://github.com/Moulberry/AxiomClientAPI) tag `v2.0.1` (`5abf940`), as JitPack built it | MIT, [LICENSE](com/github/Moulberry/AxiomClientAPI/LICENSE) | `3ff63e4a27027bb0e5389086e29855a59f0e9b444a5517666f8abc59123d5b33` |
| `com.github.moulberry.imgui-java:imgui-java-binding:aacdae671a` | [moulberry/imgui-java](https://github.com/moulberry/imgui-java) commit `aacdae671a`, as JitPack built it | MIT, [LICENSE](com/github/moulberry/imgui-java/LICENSE) | `b9a6c76762faf2487b6bbda66cc54c4c5fa5fc0d81b5f252de770b2016833271` |

These are the versions Axiom 6.1.5 for Minecraft 26.2 carries. To move to another, download the jar from `https://jitpack.io/<group as a path>/<name>/<version>/<name>-<version>.jar`, put it at the same path here, update this table and the version in `gradle.properties`, and remove the old one.
