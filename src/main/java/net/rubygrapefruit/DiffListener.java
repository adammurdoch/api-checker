package net.rubygrapefruit;

public class DiffListener {
    public void classAdded(ClassDetails details) {
    }

    public void classRemoved(ClassDetails details) {
    }

    public void classUnchanged(ClassDetails details) {
    }

    public void classChanged(ClassDetails before, ClassDetails after) {
    }

    public void superClassChanged(ClassDetails before, ClassDetails after) {
    }

    public void interfaceAdded(ClassDetails before, ClassDetails after, ClassDetails addedInterface) {
    }

    public void interfaceRemoved(ClassDetails before, ClassDetails after, ClassDetails removedInterface) {
    }

    public void methodAdded(ClassDetails before, ClassDetails after, MethodDetails addMethod) {
    }

    public void methodRemoved(ClassDetails before, ClassDetails after, MethodDetails removedMethod) {
    }
}
