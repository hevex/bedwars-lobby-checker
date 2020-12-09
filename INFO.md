# bedwars-lobby-checker
A fast and accurate lobby stat checker for bedwars.

Example implementation (in a command handler):

if (scanner != null && scanner.isRunning()) {
    scanner.getListener().finish();
}
scanner = new FastScanner(10, 5, 5, 10, 250);
scanner.inspectLobby();

"scanner" should be a FastScanner object stored within the class.
