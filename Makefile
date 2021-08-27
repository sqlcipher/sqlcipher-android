.PHONY: clean build-debug build-release \
	publish-snapshot-to-local-maven \
	publish-snapshot-to-local-nexus
GRADLE = ./gradlew

clean:
	$(GRADLE) clean

build-debug:
	$(GRADLE) assembleDebug

build-release:
	$(GRADLE) assembleRelease

publish-snapshot-to-local-maven:
	@ $(collect-signing-info) \
	$(GRADLE) \
	-PpublishSnapshot=true \
	-Psigning.keyId="$$gpgKeyId" \
	-Psigning.secretKeyRingFile="$$gpgKeyRingFile" \
	-Psigning.password="$$gpgPassword" \
	publishReleasePublicationToMavenLocal

publish-snapshot-to-local-nexus:
	@ $(collect-signing-info) \
	$(collect-nexus-info) \
	$(GRADLE) \
	-PpublishSnapshot=true \
	-Psigning.keyId="$$gpgKeyId" \
	-Psigning.secretKeyRingFile="$$gpgKeyRingFile" \
	-Psigning.password="$$gpgPassword" \
	-PnexusUsername="$$nexusUsername" \
	-PnexusPassword="$$nexusPassword" \
	-PnexusStagingProfileId="$$nexusStagingProfileId" \
	publishAllPublicationsToLocalNexusRepository

collect-signing-info := \
	read -p "Enter GPG signing key id:" gpgKeyId; \
	read -p "Enter full path to GPG keyring file \
	(possibly ${HOME}/.gnupg/secring.gpg)" gpgKeyRingFile; stty -echo; \
	read -p "Enter GPG password:" gpgPassword; stty echo;

collect-nexus-info := \
	read -p "Enter Nexus username:" nexusUsername; stty -echo; \
	read -p "Enter Nexus password:" nexusPassword; stty echo;  \
	read -p "Enter Nexus staging profile id:" nexusStagingProfileId;
