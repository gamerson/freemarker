<#escape x as x?upper_case>
<#assign x = true>${x} ${true} ${true?string}
<#assign x = false>${x} ${false} ${false?string}
<#noescape><#assign x = true>${x} ${true} ${true?string}</#noescape>
</#escape>
<#assign x = false>${x} ${false} ${false?string}
<#setting boolean_format = 'y,n'>
<#assign x = true>${x} ${true} ${true?string}
<#assign x = false>${x} ${false} ${false?string}
${'str:' + x} ${'str:' + false}
${x?string('ja', 'nein')} ${true?string('ja', 'nein')}
${beansBoolean} ${beansBoolean?string}
${booleanAndString} ${booleanAndString?string}
<#assign n = 123><#assign m = { x: 'foo', n: 'bar' }><@assertEquals actual=m['n'] + m['123'] expected='foobar' />
<@assertFails exception="UnexpectedTypeException">${m[false]}</@>
<@assertFails message="can't compare">${x == 'false'}</@>
<@assertFails message="can't compare">${x != 'false'}</@>
<@assertFails message="can't convert">${booleanVsStringMethods.expectsString(x)}</@>
<@assertEquals actual=booleanVsStringMethods.expectsString(booleanAndString) expected="theStringValue" />
<@assertEquals actual=booleanVsStringMethods.expectsBoolean(x) expected=false />
<@assertEquals actual=booleanVsStringMethods.expectsBoolean(booleanAndString) expected=true />
<@assertEquals actual=booleanVsStringMethods.overloaded(x) expected="boolean false" />
<@assertEquals actual=123?upper_case expected="123" />
<@assertEquals actual=true?upper_case expected="Y" />