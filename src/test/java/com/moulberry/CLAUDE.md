Stand-ins for Axiom's own classes, for the tests of `dev.chromastudio.client.axiom`.

- Each class here is written for Chroma Studio's tests and holds none of Axiom's code. It matches the names and signatures Chroma Studio reaches by reflection in Axiom 6.1.5, and adds public fields, such as `EditorWindowType.open` and `MaskManager.mask`, that tests set to control it.
