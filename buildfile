repositories.remote << 'http://www.ibiblio.org/maven2'
require 'buildr/scala'

ENV['USE_FSC'] = 'yes'

define 'tungsten' do
  puts Scala.version
  project.version = '0.3'
  project.group = 'tungsten'

  define 'core' do
    package :jar
    test.using :junit, :fork => false, :clonevm => false
  end

  define 'llvm' do
    package :jar
    test.using :junit, :fork => false, :clonevm => false
    compile.with project('tungsten:core')
  end
end
