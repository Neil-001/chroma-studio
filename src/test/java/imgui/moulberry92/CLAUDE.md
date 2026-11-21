Stand-ins for the ImGui binding Axiom ships, so the tests of `dev.chromastudio.client.axiom` can draw a tool's panel without native code.

- They hold none of the binding's code. Each method has the binding's signature, as the panels are compiled against the real one, and only the methods the panels call are here.
- `ImGui` lays nothing out. It records the text drawn and presses the buttons a test names in `ImGui.press`, by their label without the `##` id.
