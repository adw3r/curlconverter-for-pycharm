#!/usr/bin/env bash
# Writes updatePlugins.xml: a custom IntelliJ plugin repository with one entry, the zip of this release.
# The CI release job attaches it to the release, so
#   https://github.com/<repo>/releases/latest/download/updatePlugins.xml
# always lists the newest version, and an IDE with that URL under
# Settings → Plugins → ⚙ → Manage Plugin Repositories offers the update by itself.
#
# usage: update-plugins-xml.sh <plugin.zip> <tag> <owner/repo> [change-notes.html] > updatePlugins.xml
set -euo pipefail

zip=$1 tag=$2 repo=$3 notes=${4:-}
root=$(cd "$(dirname "$0")/../.." && pwd)

prop() { sed -n "s/^$1 *= *//p" "$root/plugin/gradle.properties"; }
version=$(prop pluginVersion)
since=$(prop pluginSinceBuild)
xml="$root/plugin/src/main/resources/META-INF/plugin.xml"
id=$(sed -n 's:.*<id>\(.*\)</id>.*:\1:p' "$xml" | head -n1)
name=$(sed -n 's:.*<name>\(.*\)</name>.*:\1:p' "$xml" | head -n1)

[[ -n $version && -n $since && -n $id && -n $name ]] || { echo "can't read version/since-build/id/name" >&2; exit 1; }
[[ $tag == "v$version" ]] || { echo "tag $tag != v$version" >&2; exit 1; }

url="https://github.com/$repo/releases/download/$tag/$(basename "$zip")"

cat <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<plugins>
  <plugin id="$id" url="$url" version="$version">
    <idea-version since-build="$since"/>
    <name>$name</name>
    <vendor url="https://github.com/$repo">adw3r</vendor>
    <description><![CDATA[Converts cURL commands to Python (curl_cffi by default) and 40+ other languages with the bundled curlconverter, offline.<br>Source and releases: <a href="https://github.com/$repo">github.com/$repo</a>]]></description>
EOF
if [[ -n $notes && -s $notes ]]; then
  printf '    <change-notes><![CDATA[%s]]></change-notes>\n' "$(sed 's/]]>/]]]]><![CDATA[>/g' "$notes")"
fi
cat <<EOF
  </plugin>
</plugins>
EOF
