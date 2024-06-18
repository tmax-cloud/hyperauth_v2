#!/bin/bash

# destination file
output=./CHANGELOG.md
timestamp=`date +"%Y-%m-%d %H:%M:%S"`
version=$1
prev_version=$2
keycloak_base_version=$3

if [ -z $version ]; then
version=4.0.0.x
fi

if [ -z $prev_version ]; then
prev_version=4.0.0.0
fi

if [ -z $keycloak_base_version ]; then
keycloak_base_version=23.0.6
fi

backup="./CHANGELOG_$prev_version.md"
if [ -f $output ]; then
echo "!!!changelog file exist"
mv $output $backup
fi

echo "workdir: `pwd`"
echo "!!!make changelog for $version"
echo "!!!prev version: $prev_version"

# gen file
echo "# [HyperAuth_v2 CHANGE_LOG]" > $output
echo "All notable changes to this project will be documented in this file." >> $output

echo -e "\n<!-------------------- $version start -------------------->" >> $output
echo -e "\n## HyperAuthServer $version ($timestamp)" >> $output

echo -e "\n#### Based - Keycloak version $keycloak_base_version" >> $output

# make commit log to changelog
echo -e "\n### Added" >> $output
git log b$prev_version..HEAD --no-merges --oneline --format="  - %s by %cN" --grep="^feat:.*" -i >> $output
#git log v$prev_version..HEAD --no-merges --oneline --format="  - %s by %cN" >> $output

echo -e "\n### Changed" >> $output
git log b$prev_version..HEAD --no-merges --oneline --format="  - %s by %cN" --grep="^mod:.*" -i >> $output

echo -e "\n### Fixed" >> $output
git log b$prev_version..HEAD --no-merges --oneline --format="  - %s by %cN" --grep="^ims\[[0-9]*\].*" -i >> $output

echo -e "\n### CRD yaml" >> $output
git log b$prev_version..HEAD --no-merges --oneline --format="  - %s by %cN" --grep="^crd:.*" -i >> $output

echo -e "\n### Etc" >> $output
git log b$prev_version..HEAD --no-merges --oneline --format="  - %s by %cN" --grep="^etc:.*" -i >> $output

echo -e "\n<!--------------------- v$version end --------------------->" >> $output

if [ -f $backup ]; then
echo "!!!add previous changelog"
sed '1,2d' $backup >> $output
rm $backup
fi

echo "!!!done"