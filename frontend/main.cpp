#include <windows.h>
#include <iostream>
#include <string>

int main() {

    std::cout << "Testing suspicious API usage...\n";

    // Suspicious-looking memory allocation
    LPVOID mem = VirtualAlloc(
        NULL,
        1024,
        MEM_COMMIT | MEM_RESERVE,
        PAGE_READWRITE
    );

    if (mem != NULL) {
        std::cout << "Memory allocated.\n";
    }

    // Suspicious-looking API import
    WinExec("notepad.exe", SW_SHOW);

    // Random high-entropy string
    std::string junk =
        "A9F2KLMXZQWERTYUIOP1234567890ABCDEFG";

    std::cout << junk << std::endl;

    return 0;
}