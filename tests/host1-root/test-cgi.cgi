#!/usr/bin/perl -w

print "Content-type: text/html\r\n\r\n";
print "<HTML>\n";
print "<BODY>\n";

print "<TITLE>Server-provided Environment variables</TITLE>\n";
print "<TABLE>\n";
print "<TR><TD colspan=2 align=center>Environment Variables</TD></TR>\
+n";
foreach my $e (keys %ENV) {
  print "<TR><TD>$e</TD><TD>$ENV{$e}</TD></TR>\n";
}
print "</TABLE>\n";

print "<p>Standard input: ";
while (<>) {
print;
}
print "</BODY></HTML>\n";
