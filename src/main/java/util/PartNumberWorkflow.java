package util;

import java.awt.Toolkit;
import java.awt.datatransfer.*;
import java.io.IOException;

public class PartNumberWorkflow implements ClipboardOwner {

    // Volatile flag to safely signal the change across threads
    private volatile boolean ownershipLost = false;

    /**
     * Step 1 & 2: Places the part number and waits for the user to copy new data.
     */
    public String executeCycle(String partNumberToLookUp) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        ownershipLost = false; // Reset flag for new cycle

        // --- 1. Program Pastes Part Number ---
        StringSelection selection = new StringSelection(partNumberToLookUp);
        // Set content, assigning 'this' as the owner
        clipboard.setContents(selection, this);
        System.out.println("-> Program set part number: " + partNumberToLookUp);
        System.out.println("   Waiting for user to copy new info...");

        // --- 2. Program Waits ---
        while (!ownershipLost) {
            try {
                // Wait for the lostOwnership signal (User copied new data)
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        System.out.println("✅ New content detected!");

        // --- 3. Program Reads New Info ---
        return pasteFromClipboard(clipboard);
    }

    /**
     * Called by the system when our content is replaced. This is the trigger.
     */
    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
        ownershipLost = true;
    }

    /**
     * Helper method to safely read the text from the clipboard.
     */
    private String pasteFromClipboard(Clipboard clipboard) {
        try {
            Transferable contents = clipboard.getContents(null);
            if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                try {
                    String result = (String) contents.getTransferData(DataFlavor.stringFlavor);
                    return result.trim();
                } catch (UnsupportedFlavorException | IOException e) {
                    // Handle read errors
                    System.err.println("Error reading clipboard: " + e.getMessage());
                }
            }
        }catch (IllegalStateException e){
            System.err.println("Illegal state exception: " + e.getMessage());
        }
        return null;
    }
}