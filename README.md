Ace Combat 3 Bin Splitter
by Dashman

How to use this
----------------------

This is a command line program, so open a shell / cmd window.
As always, this is a java applet. You'll need to have Java installed for this to work. 
Put the program in the same folder as the files the program is affecting (I was too lazy to implement subfolder recognition).

** To split:
java -jar bin_splitter.jar -s <filename> [<destination_folder>]

For example, 
java -jar bin_splitter.jar -s 0015.bin 0015-extract

If the destination folder is not specified, the files will be extracted to the current folder. Extracted files will be extracted as 0000.TIM, 0001.TIM and so on.

The split process will create an extra file "file.list" that is needed during the merging process.


** To merge:
java -jar bin_splitter.jar -m <filename> <file_list>

For example,
java -jar bin_splitter.jar -m 0015_new.bin file.list

Remember that ALL files (the ones that were extracted, modified or not) should be present in the same folder or funny things will happen. Funny as in bad.