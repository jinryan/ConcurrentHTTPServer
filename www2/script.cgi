$buffer = $ENV{'QUERY_STRING'}; 
#split information into key/value pairs 
@pairs = split(/&/, $buffer); 
foreach $pair (@pairs)  
{ 
    ($name, $value) = split(/=/, $pair); 
    $value =~ tr/+/ /; 
    $value =~ s/%([a-fA-F0-9] [a-fA-F0-9])/pack("C", hex($1))/eg; 
    $value =~ s/~!/ ~!/g; 
    $FORM{$name} = $value; 
} 
  
$SearchTerm = $FORM{'q'}; 
$Location = $FORM{'l'}; 
  
print "Content-type:text/html\r\n\r\n"; 
print "<html>"; 
print "<head>"; 
print "<title>CGI Return Value</title>"; 
print "</head>"; 
print "<body>"; 
print "<h3>Hello You searched '$Location' for '$SearchTerm'<br> 
Few Matches Found!<br> 
<br> 
Match 1<br> 
Match 2<br> 
Match 3<br> 
Match 4<br> 
etc.....</h3>"; 
print "</body>"; 
print "</html>"; 
exit(0);