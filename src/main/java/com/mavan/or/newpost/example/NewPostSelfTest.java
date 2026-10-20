package com.mavan.or.newpost.example;

/** Fast regression entry point for the New Post module. */
public final class NewPostSelfTest {
    public static void main(String[] args) {
        NewPostTinyExactTest.main(args);
        NewPostDocumentedExampleCheck.main(new String[]{"1"});
        System.out.println("All fast New Post self-tests PASSED");
    }
}
