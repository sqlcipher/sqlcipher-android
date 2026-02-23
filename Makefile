.PHONY: clean build-debug build-release \
	publish-snapshot-to-local-maven \
	publish-snapshot-to-local-nexus test
GRADLE = ./gradlew

clean:
	-rm -rf build
	$(GRADLE) clean

test:
	ANDROID_SERIAL=$(shell adb devices | tail -n +2 | awk '!/emulator/{print $$1}') \
	$(GRADLE) :sqlcipher:connectedDebugAndroidTest

build-debug:
	$(GRADLE) assembleDebug

build-release:
	$(GRADLE) assembleRelease

publish-remote-release:
	@ $(collect-signing-info) \
	$(collect-nexus-info) \
	$(GRADLE) \
	-PpublishSnapshot=false \
	-PpublishLocal=false \
	-PdebugBuild=false \
	-PsigningKeyId="$$gpgKeyId" \
	-PsigningKeyRingFile="$$gpgKeyRingFile" \
	-PsigningKeyPassword="$$gpgPassword" \
	-PnexusUsername="$$nexusUsername" \
	-PnexusPassword="$$nexusPassword" \
	sqlcipher:publish

collect-signing-info := \
	read -p "Enter GPG signing key id:" gpgKeyId; \
	read -p "Enter full path to GPG keyring file \
	(possibly ${HOME}/.gnupg/secring.gpg)" gpgKeyRingFile; stty -echo; \
	read -p "Enter GPG password:" gpgPassword; stty echo;

collect-nexus-info := \
	read -p "Enter Nexus username:" nexusUsername; \
	stty -echo; read -p "Enter Nexus password:" nexusPassword; stty echo;
