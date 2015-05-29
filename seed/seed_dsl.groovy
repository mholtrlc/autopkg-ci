streamFileFromWorkspace("${RECIPE_LIST_FILE}").eachLine {

  def recipeName = it
  def emailRecipients = "\${DEFAULT_RECIPIENTS}"
  def emailSubject = "\${PROJECT_NAME} - \${BUILD_STATUS}"
  def emailBody = "\${BUILD_LOG}"

  job {
    name "${recipeName}"

    //logRotator(int daysToKeepInt, int numToKeepInt, int artifactDaysToKeepInt, int artifactNumToKeepInt)
    logRotator(30, -1, -1, -1)
    label('master')

    multiscm {
      git('git://github.com/autopkg/autopkg.git', 'master')
    }

    triggers {
      cron('H H(0-7),H(8-15),H(16-23) * * *')
    }

    steps {
      shell("echo ${recipeName} > recipe.txt")
      shell("/usr/local/bin/sshfs ubuntu@munki01.reallifechurch.org:/var/www/munki/ /Users/Shared/Jenkins/munki_repo/ -o volname=munki_repo,auto_cache,reconnect,defer_permissions,negative_vncache")
      shell(readFileFromWorkspace('autopkg-ci/steps/autopkg_run.py'))
      shell("/usr/local/munki/makecatalogs /Users/Shared/Jenkins/munki_repo")
      shell("/sbin/umount /Users/Shared/Jenkins/munki_repo")
    }

    publishers {
      extendedEmail(emailRecipients, emailSubject, emailBody) {
        trigger('Failure')
        trigger('Fixed')
        trigger(triggerName: 'StillFailing', subject: emailSubject, body:emailBody, recipientList:emailRecipients,
            sendToDevelopers: false, sendToRequester: false, includeCulprits: false, sendToRecipientList: false)
      }
    }

    configure { project ->
      def setter = project / publishers / 'hudson.plugins.descriptionsetter.DescriptionSetterPublisher'
      setter / regexp << '^PARSED_VERSION (.*)'
      setter / regexpForFailed << ''
      setter / setForMatrix << 'false'
    }
  }
}
