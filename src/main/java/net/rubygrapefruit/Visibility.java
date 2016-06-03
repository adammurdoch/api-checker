package net.rubygrapefruit;

import org.objectweb.asm.Opcodes;

public enum Visibility {
    Private,
    PackageProtected,
    Protected,
    Public;

    public static Visibility fromAccessField(int field) {
        if ((field & 0xff & Opcodes.ACC_PUBLIC) != 0) {
            return Public;
        }
        if ((field & 0xff & Opcodes.ACC_PROTECTED) != 0) {
            return Protected;
        }
        if ((field & 0xff & Opcodes.ACC_PRIVATE) == 0) {
            return PackageProtected;
        }
        return Private;
    }
}
