/*
 * Ex1.exe - double-click launcher of the Ex1 app on Windows.
 *
 * Starts the app with the Java runtime that ships next to it (runtime\bin\javaw.exe), so nothing
 * has to be installed: download, double-click, done. A file dropped on the icon (a .nrrd, and
 * optionally a .csv) is handed to the app, which opens it.
 *
 * Fallbacks, in order, when the bundled runtime is missing: JAVA_HOME, the JDKs IntelliJ and
 * Android Studio download into %USERPROFILE%\.jdks, then javaw.exe on the PATH.
 *
 * Build (Linux, mingw-w64):  scripts/build_launcher.sh
 */

#include <windows.h>
#include <wchar.h>

#define PATH_CHARS 4096
#define CMD_CHARS 32768

/**
 * @param (const wchar_t*) path file to test
 * @return nonzero when the file exists and is not a folder
 */
static int isFile(const wchar_t *path)
{
    DWORD attributes = GetFileAttributesW(path);
    return attributes != INVALID_FILE_ATTRIBUTES && (attributes & FILE_ATTRIBUTE_DIRECTORY) == 0;
}

/**
 * The newest JDK in %USERPROFILE%\.jdks that has bin\javaw.exe (folder names sort by version).
 *
 * @param (wchar_t*) out receives the path of javaw.exe
 * @return nonzero when one was found
 */
static int findInJdks(wchar_t *out)
{
    wchar_t profile[PATH_CHARS];
    wchar_t pattern[PATH_CHARS];
    wchar_t candidate[PATH_CHARS];
    WIN32_FIND_DATAW entry;
    HANDLE search;
    int found = 0;
    if (GetEnvironmentVariableW(L"USERPROFILE", profile, PATH_CHARS) == 0)
    {
        return 0;
    }
    swprintf(pattern, PATH_CHARS, L"%ls\\.jdks\\*", profile);
    search = FindFirstFileW(pattern, &entry);
    if (search == INVALID_HANDLE_VALUE)
    {
        return 0;
    }
    do
    {
        if ((entry.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) != 0 && entry.cFileName[0] != L'.')
        {
            swprintf(candidate, PATH_CHARS, L"%ls\\.jdks\\%ls\\bin\\javaw.exe", profile, entry.cFileName);
            if (isFile(candidate) && (!found || wcscmp(candidate, out) > 0))
            {
                wcscpy(out, candidate);
                found = 1;
            }
        }
    }
    while (FindNextFileW(search, &entry));
    FindClose(search);
    return found;
}

/**
 * @param (const wchar_t*) home the folder next to Ex1.exe
 * @param (wchar_t*) out receives the path of javaw.exe
 * @return nonzero when a Java runtime was found
 */
static int findJava(const wchar_t *home, wchar_t *out)
{
    wchar_t javaHome[PATH_CHARS];
    wchar_t *unused;
    swprintf(out, PATH_CHARS, L"%ls\\runtime\\bin\\javaw.exe", home);
    if (isFile(out))
    {
        return 1;
    }
    if (GetEnvironmentVariableW(L"JAVA_HOME", javaHome, PATH_CHARS) > 0)
    {
        swprintf(out, PATH_CHARS, L"%ls\\bin\\javaw.exe", javaHome);
        if (isFile(out))
        {
            return 1;
        }
    }
    if (findInJdks(out))
    {
        return 1;
    }
    return SearchPathW(NULL, L"javaw.exe", NULL, PATH_CHARS, out, &unused) > 0;
}

/**
 * The arguments of this process as typed (everything after the program name), so quoting of
 * dropped file names survives unchanged.
 *
 * @return pointer into the process command line
 */
static const wchar_t *ownArguments(void)
{
    const wchar_t *p = GetCommandLineW();
    if (*p == L'"')
    {
        p++;
        while (*p != L'\0' && *p != L'"')
        {
            p++;
        }
        if (*p == L'"')
        {
            p++;
        }
    }
    else
    {
        while (*p != L'\0' && *p != L' ' && *p != L'\t')
        {
            p++;
        }
    }
    while (*p == L' ' || *p == L'\t')
    {
        p++;
    }
    return p;
}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE previous, PWSTR args, int show)
{
    static wchar_t home[PATH_CHARS];
    static wchar_t java[PATH_CHARS];
    static wchar_t jar[PATH_CHARS];
    static wchar_t command[CMD_CHARS];
    wchar_t *slash;
    STARTUPINFOW startup;
    PROCESS_INFORMATION process;
    (void) instance;
    (void) previous;
    (void) args;
    (void) show;

    GetModuleFileNameW(NULL, home, PATH_CHARS);
    slash = wcsrchr(home, L'\\');
    if (slash != NULL)
    {
        *slash = L'\0';
    }
    // The jar of a source checkout is build\Ex0.jar; a packaged app may keep it in app\.
    swprintf(jar, PATH_CHARS, L"%ls\\build\\Ex0.jar", home);
    if (!isFile(jar))
    {
        swprintf(jar, PATH_CHARS, L"%ls\\app\\Ex0.jar", home);
    }
    if (!isFile(jar))
    {
        MessageBoxW(NULL, L"build\\Ex0.jar is missing next to Ex1.exe.\n\n"
                    L"Download the whole folder again (it must contain build\\Ex0.jar and runtime\\).",
                    L"Ex1", MB_OK | MB_ICONERROR);
        return 1;
    }
    if (!findJava(home, java))
    {
        MessageBoxW(NULL, L"The Java runtime that comes with Ex1 (the runtime folder) is missing, "
                    L"and no other Java 17+ was found.\n\nDownload the whole folder again.",
                    L"Ex1", MB_OK | MB_ICONERROR);
        return 1;
    }
    swprintf(command, CMD_CHARS, L"\"%ls\" -jar \"%ls\" %ls", java, jar, ownArguments());

    ZeroMemory(&startup, sizeof(startup));
    startup.cb = sizeof(startup);
    ZeroMemory(&process, sizeof(process));
    // Working folder = the app's folder, so the samples in data\ are found.
    if (!CreateProcessW(java, command, NULL, NULL, FALSE, 0, NULL, home, &startup, &process))
    {
        wchar_t message[PATH_CHARS + 128];
        swprintf(message, PATH_CHARS + 128, L"Could not start Java (error %lu):\n%ls", GetLastError(), java);
        MessageBoxW(NULL, message, L"Ex1", MB_OK | MB_ICONERROR);
        return 1;
    }
    CloseHandle(process.hThread);
    CloseHandle(process.hProcess);
    return 0;
}
