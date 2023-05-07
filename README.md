# Java code analysis

I want to look at the space of program transformation and refactoring particularly at the larger scale architectural and design choices.

What I would like to have as a tool is something that does the following

1. With assistance restructure code to match new requirements. While maintaining existing behaviour.
2. Alternately expose breaking changes and localise them so that it is easier to reduce manual work where required
3. Automatically update tests or provide the ability to easily track breaking tests and migrate them to the new architecture.

This ideal may not be practical as most architectural and design changes lead to some fundamental change in behaviour as they can impact both non-functional and functional aspects.

There may be practical limitations as to what an automated system can do in the face of undecidabililty and intractibility.

This program is built on top of Javaparser. It takes the parsed ast and writes it into an sqlite db.

