#!/usr/bin/env python3
"""Demo entry point of Ex1, in the exact form the assignment names:

    python Ex0.py file.nrrd file.csv file.mp4 [options]

The projection itself is implemented in Java (build/Ex0.jar). This script only finds a Java
runtime - the one shipped in runtime/ on Windows, else any JDK/JRE 17+ - and hands the arguments
over, so the grader can use the command from the assignment sheet without installing anything. With no arguments it opens
the window app. Options are passed through unchanged (see README: --interp, --fps, --window,
--frames, --threads, --strict).

Exit codes are the Java program's: 0 ok, 1 usage, 2 bad input, 3 output error; 4 = no Java found.
"""

import glob
import os
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
JAR = os.path.join(HERE, "build", "Ex0.jar")
MIN_JAVA = 17
EXIT_NO_JAVA = 4


def candidate_javas():
    """Yield possible java executables, most specific first."""
    exe = "java.exe" if os.name == "nt" else "java"
    # The runtime that ships with the app (runtime/, Windows) first: nothing to install.
    yield os.path.join(HERE, "runtime", "bin", exe)
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        yield os.path.join(java_home, "bin", exe)
    home = os.path.expanduser("~")
    patterns = [
        os.path.join(home, ".jdks", "*", "bin", exe),                      # IntelliJ / Android Studio downloads
        os.path.join(os.environ.get("ProgramFiles", r"C:\Program Files"), "Java", "*", "bin", exe),
        os.path.join(os.environ.get("ProgramFiles", r"C:\Program Files"), "Eclipse Adoptium", "*", "bin", exe),
        os.path.join(os.environ.get("ProgramFiles", r"C:\Program Files"), "Microsoft", "*", "bin", exe),
        os.path.join(os.environ.get("ProgramFiles", r"C:\Program Files"), "Android", "Android Studio", "jbr", "bin", exe),
        "/usr/lib/jvm/*/bin/java",
        "/Library/Java/JavaVirtualMachines/*/Contents/Home/bin/java",
        "/opt/homebrew/opt/openjdk*/bin/java",
    ]
    for pattern in patterns:
        for path in sorted(glob.glob(pattern), reverse=True):
            yield path
    on_path = shutil.which("java")
    if on_path:
        yield on_path


def java_major_version(java):
    """Return the major version of a java executable, or 0 if it cannot be run."""
    try:
        out = subprocess.run([java, "-version"], capture_output=True, text=True, timeout=20)
    except (OSError, subprocess.SubprocessError):
        return 0
    text = (out.stderr or "") + (out.stdout or "")
    for token in text.replace('"', " ").split():
        parts = token.split(".")
        if parts and parts[0].isdigit():
            major = int(parts[0])
            if major == 1 and len(parts) > 1 and parts[1].isdigit():
                major = int(parts[1])          # "1.8.0" style
            if major >= 8:
                return major
    return 0


def find_java():
    """First java that is new enough, or None."""
    seen = set()
    for java in candidate_javas():
        java = os.path.normpath(java)
        if java in seen or not os.path.isfile(java):
            continue
        seen.add(java)
        if java_major_version(java) >= MIN_JAVA:
            return java
    return None


def ensure_jar(java):
    """Build the jar with the JDK's javac when it is missing (source checkout without build/)."""
    if os.path.isfile(JAR):
        return True
    script = os.path.join(HERE, os.path.join("scripts", "build.bat" if os.name == "nt" else "build.sh"))
    if not os.path.isfile(script):
        return False
    javac = os.path.join(os.path.dirname(java), "javac.exe" if os.name == "nt" else "javac")
    if not os.path.isfile(javac):
        print("build/Ex0.jar is missing and this Java has no compiler; run build.bat / build.sh with a JDK.",
              file=sys.stderr)
        return False
    env = dict(os.environ)
    env["PATH"] = os.path.dirname(java) + os.pathsep + env.get("PATH", "")
    cmd = [script] if os.name == "nt" else ["bash", script]
    return subprocess.run(cmd, env=env, cwd=HERE).returncode == 0 and os.path.isfile(JAR)


def main(argv):
    java = find_java()
    if java is None:
        print("Java 17 or newer was not found. Install a JDK (https://adoptium.net) or set JAVA_HOME.",
              file=sys.stderr)
        return EXIT_NO_JAVA
    if not ensure_jar(java):
        return EXIT_NO_JAVA
    cmd = [java, "-Xmx2g", "-jar", JAR] + argv
    return subprocess.call(cmd)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
