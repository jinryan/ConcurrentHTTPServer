#!/usr/bin/perl -w

print "Content-Type: text/html\r\n";
print "\r\n";
$company = "Apple";
print "<html>";
print "<p>The price of $company is ";
if ($company =~ /appl/) {
  my $var_rand = rand();
  print 450 + 10 * $var_rand;
} else {
  print "150";
}

print "<p>Debug: all environment variables:";
print "<pre>";
foreach (sort keys %ENV) {
  print "$_  =  $ENV{$_}\n";
}
print "</pre>";

print "<p>Debug: STDIN:";
print "<pre>";
foreach my $line ( <STDIN> ) {
    chomp( $line );
    print "$line\n";
}
print "</pre>";
print "</html>";
