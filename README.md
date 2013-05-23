## SoundWaves

SoundWaves, a fork of [Hapi Podcast] [play],
an Android app for playing and managing and syncing your podcasts.

I started this project because the Android echosystem seriously lacked a
pro-user podcast app. This app is trying to be the good guy by:

* Being released as free and open source software
* Being free of cost
* Privacy aware
* Cloud based and multi device friendly
* User friendly 

This git repository includes the full history that is available in the
Hapi Podcast [svn repository] [svnrepo].

  [play]: https://play.google.com/store/apps/details?id=info.bottiger.podcast
  [gitrepo]: https://github.com/bottiger/SoundWaves

It has a few new features and a rearranged UI.

Thanks a ton to xuluan for making this open-source podcaster and helping me
get started with my own fork.

Arvid Böttiger

BETA Testing
============

While there are still some critical bugs in the code it has become usable for everyday use - if you are fine with random crashes once in a while.
In order to get the current beat-version Google requires you to join this community on Google+

    https://plus.google.com/communities/112499725575016472220

Then you can join the beta program on the play store from here:

    https://play.google.com/apps/testing/org.bottiger.podcast

Ignore SoundWaves.java
======================

After you fill in the sensitive information in SoundWaves.java use the following line to avoid commiting it:

    git update-index --assume-unchanged src/info/bottiger/podcast/SoundWaves.java
    git update-index --assume-unchanged AndroidManifest.xml

if you make changes you can start tracking it again with

    git update-index --no-assume-unchanged src/info/bottiger/podcast/SoundWaves.java
    git update-index --no-assume-unchanged AndroidManifest.xml
