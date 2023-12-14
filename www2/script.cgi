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

print "<p>
Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Elementum nibh tellus molestie nunc non blandit massa enim. Egestas erat imperdiet sed euismod nisi porta. Quis varius quam quisque id diam vel quam elementum pulvinar. Eget velit aliquet sagittis id. Dignissim sodales ut eu sem integer vitae. Nisi lacus sed viverra tellus in hac habitasse platea dictumst. Egestas integer eget aliquet nibh praesent tristique. Morbi tempus iaculis urna id volutpat lacus laoreet non. Viverra maecenas accumsan lacus vel facilisis. Donec enim diam vulputate ut pharetra sit. Volutpat odio facilisis mauris sit amet massa vitae. Purus viverra accumsan in nisl nisi scelerisque eu. Imperdiet nulla malesuada pellentesque elit eget gravida cum.

Sit amet facilisis magna etiam tempor orci eu. Et pharetra pharetra massa massa ultricies mi. Varius quam quisque id diam vel quam elementum pulvinar etiam. Ut etiam sit amet nisl. Ut sem nulla pharetra diam sit amet. Tempus egestas sed sed risus pretium. Vivamus at augue eget arcu dictum varius duis at. Non pulvinar neque laoreet suspendisse interdum consectetur libero. Eget mi proin sed libero enim sed faucibus turpis. Laoreet id donec ultrices tincidunt arcu non sodales neque sodales. Elementum facilisis leo vel fringilla. Sem fringilla ut morbi tincidunt augue interdum velit euismod. Duis ultricies lacus sed turpis tincidunt id aliquet. Posuere morbi leo urna molestie. Consequat mauris nunc congue nisi vitae. Hac habitasse platea dictumst quisque sagittis purus.

Orci eu lobortis elementum nibh tellus molestie nunc non blandit. Eu tincidunt tortor aliquam nulla. Varius sit amet mattis vulputate enim. Sed enim ut sem viverra aliquet. Felis donec et odio pellentesque diam volutpat commodo sed egestas. Quis eleifend quam adipiscing vitae proin sagittis nisl. Dictum non consectetur a erat nam at. Ultrices tincidunt arcu non sodales neque sodales ut etiam. Tortor posuere ac ut consequat semper viverra nam libero. Sagittis purus sit amet volutpat consequat. Ac turpis egestas maecenas pharetra convallis.

Nec feugiat in fermentum posuere. Dis parturient montes nascetur ridiculus mus. Ac odio tempor orci dapibus ultrices. Nunc pulvinar sapien et ligula. Urna condimentum mattis pellentesque id. Tincidunt arcu non sodales neque sodales ut etiam. Magnis dis parturient montes nascetur. Commodo sed egestas egestas fringilla phasellus faucibus scelerisque eleifend donec. Imperdiet dui accumsan sit amet nulla. Congue eu consequat ac felis donec et odio pellentesque diam. Cras tincidunt lobortis feugiat vivamus at. Orci eu lobortis elementum nibh tellus molestie nunc non. In tellus integer feugiat scelerisque varius morbi enim nunc faucibus. Adipiscing elit ut aliquam purus sit amet luctus. Consequat nisl vel pretium lectus quam id leo in. Neque sodales ut etiam sit amet. Egestas purus viverra accumsan in nisl nisi scelerisque eu. Nisl vel pretium lectus quam.

Lobortis feugiat vivamus at augue eget. Nisl vel pretium lectus quam id leo in vitae turpis. Fusce id velit ut tortor pretium viverra suspendisse potenti. Egestas tellus rutrum tellus pellentesque eu. Tempus iaculis urna id volutpat lacus laoreet non. Pulvinar neque laoreet suspendisse interdum consectetur. Maecenas ultricies mi eget mauris pharetra et ultrices neque. Mauris vitae ultricies leo integer malesuada nunc. Quam elementum pulvinar etiam non quam lacus. Sed faucibus turpis in eu mi. Nulla facilisi morbi tempus iaculis urna id.

Magna eget est lorem ipsum dolor sit amet consectetur. Habitant morbi tristique senectus et netus et malesuada fames ac. Neque ornare aenean euismod elementum nisi. Mi eget mauris pharetra et ultrices neque ornare. Nunc congue nisi vitae suscipit tellus mauris a diam maecenas. Accumsan tortor posuere ac ut consequat semper viverra nam libero. Nec tincidunt praesent semper feugiat. Platea dictumst vestibulum rhoncus est pellentesque elit. At elementum eu facilisis sed odio morbi quis commodo. Neque aliquam vestibulum morbi blandit cursus risus at ultrices mi.

Blandit turpis cursus in hac. Luctus accumsan tortor posuere ac ut consequat semper viverra. Gravida quis blandit turpis cursus in hac habitasse platea dictumst. Vitae turpis massa sed elementum. Sagittis id consectetur purus ut faucibus. Leo duis ut diam quam. Mi in nulla posuere sollicitudin aliquam. Vivamus at augue eget arcu dictum. Luctus accumsan tortor posuere ac ut consequat semper viverra. Tincidunt id aliquet risus feugiat in ante metus dictum. In egestas erat imperdiet sed euismod nisi porta lorem. Nibh tellus molestie nunc non blandit massa. Quis vel eros donec ac.

Id donec ultrices tincidunt arcu non sodales neque. Elit sed vulputate mi sit. Praesent tristique magna sit amet purus. Laoreet id donec ultrices tincidunt arcu non sodales. Quam id leo in vitae turpis. Dignissim suspendisse in est ante in nibh mauris cursus. Faucibus scelerisque eleifend donec pretium. In cursus turpis massa tincidunt dui ut. Senectus et netus et malesuada fames ac turpis egestas integer. Volutpat est velit egestas dui id ornare arcu odio. Sed libero enim sed faucibus turpis in eu mi bibendum. Quis risus sed vulputate odio. Ante metus dictum at tempor commodo. Tristique senectus et netus et malesuada fames. Nunc non blandit massa enim nec dui nunc mattis. Sit amet venenatis urna cursus eget nunc scelerisque viverra mauris. Id interdum velit laoreet id donec ultrices tincidunt.
</p>";


print "</body>"; 
print "</html>"; 
exit(0);