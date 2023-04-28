FreeOTP Backup Decryptor
========================

[FreeOTP Android](https://github.com/freeotp/freeotp-android) supports external backup. This code decrypts the backup and exposes secret data.

This code is just a proof-of-concept.



-----

## USE WITH CAUTION

THIS CODE **EXPOSES** VERY SENSITIVE DATA. (secret sauce of your OTP code; anyone who knows the sauce can re-produce your OTP.)

THIS MAY MAKE YOUR 2-FACTOR-AUTHENTICATION MEANINGLESS AND ALSO MAKE YOUR SECURITY WEAKER.

USE WITH CAUTION; THE AUTHOR OF THIS CODE ACCEPTS NO RESPONSIBILITY.



### Then, why do I make this code?

Most of it is just curiosity :)
If it is required to migrate from FreeOTP to other tool like Google Authenticator, this code will be helpful.



-----

## Basic Concepts

FreeOTP uses 2-stage encryption.

Your secret sauce (OTP) is encrypted using AES encryption with a randomly generated symmetric key. We will call this key "Key 1".
As "Key 1" is a very long, randomly generated number, it is virtually impossible to estimate or brute-force.
So, your secret sauce is as secure as your "Key 1."

Then how do we store the Key1 securely?
"Key 1" itself is also encrypted using AES with another key. We will refer to this other key as "Key 2."
As a result, your "Key 1" is as secure as your "Key 2."

"Key 2" is derived from your "backup password" using a one-way hash function.
Because this derivation process is time-consuming, it is very hard to brute-force "Key 2."
However, if the "backup password" is weak, it becomes possible to estimate it.
THEREFORE, USE A LONG AND STRONG PASSWORD!

Then why does FreeOTP use 2-stage encryption? By doing so, it is easy to change the "backup password".
Let's assume only one-stage encryption is applied (i.e., all the secret sauces are encrypted using "Key 2" directly).
When the password is changed, "Key 2" is also changed, and all your secret sauces should be decrypted then re-encrypted.

However, by adopting 2-stage encryption, it is only necessary to re-encrypt "Key 1" when the backup password is changed.



-----

## LICENSE

The parts I did will be published as unlicensed. Please read `UNLICENSE.txt."

Many parts of this repo are copied from [FreeOTP Android](https://github.com/freeotp/freeotp-android), which is licensed under Apache License 2.0.

