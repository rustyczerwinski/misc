import groovy.transform.Field
@Field private helper = null

def initHelper(help)
{
  helper = help
}

def runUnitTest() {
  sh 'env | sort'
  sh '''
    pwd
    hostname
    . env/bin/activate
    python3.5 -m pip install -r requirements.txt
    python3.5 -m pylint -f parseable sfprime sfhme virthci > pylint.out || true
    REDIS_PORT=0 LOG_TO_CONSOLE=False python3.5 -m nose --xunit-file=nosetests-sfprime.xml --with-xunit  --with-coverage --cover-package=sfprime --cover-inclusive --cover-erase --exe test/ || true

    mv .coverage coverage.sfprime

    REDIS_PORT=0 LOG_TO_CONSOLE=False python3.5 -m nose --xunit-file=nosetests-sfhme.xml --with-xunit --with-coverage --cover-package=sfhme --cover-inclusive --cover-erase --exe sfhme/test/ || true
    mv .coverage coverage.sfhme

    LOG_TO_CONSOLE=False python3.5 -m nose --xunit-file=nosetests-virthci.xml --with-xunit --with-coverage --cover-package=virthci --cover-inclusive --cover-erase --exe virthci/test/ || true
    mv .coverage coverage.virthci

    REDIS_PORT=0 LOG_TO_CONSOLE=False python3.5 -m nose --xunit-file=nosetests-nodebeacon.xml --with-xunit  --with-coverage --cover-package=install/blaze/sf/bin/ --cover-inclusive --cover-erase --exe testscript/node-beacon_test.py || true
    mv .coverage coverage.nodebeacon

    python3.5 -m coverage combine coverage.sfprime coverage.sfhme coverage.virthci coverage.nodebeacon
    python3.5 -m coverage xml --include=sfprime*,testapi*,sfhme*,virthci*,nodebeacon*
    cd system_test; docker build . -f Dockerfile -t sfprime-auto:${SHA}; cd ..
    docker run --rm -i -v $(pwd)/system_test:/sfprime-auto/ sfprime-auto:${SHA} scripts/hci-vl.sh ./jenkins_test.sh
  '''
  helper.changeWSOwner(JENKINS_SLAVE_UID, JENKINS_SLAVE_GID)
}

def runHappyPath(p)
{

  // Show what was passed in. Mainly for debugging purposes.
  echo "${p}"
  // Get upgrade packages if we generated them earlier. If not we will rebuild.
  try {
    sh 'rm -rf dist; mkdir dist'
    copyArtifacts(projectName: "${JOB_NAME}", selector: specific("${BUILD_NUMBER}"), filter: 'dist/upgrade-*.tar', target: '.')
  }
  catch(error) {
    echo "Failed to get previous upgrade packages, rebuilding. Error: ${error}"
    sh 'rm -rf dist'
  }

  sh '''

  . ./bin/exports.sh

  bin/run-in-container --docker_image=${SFPRIME_BUILD_IMAGE} --workdir=/temp rm -rf system_test/artifacts/*

  if [ -d venv ]; then
    rm -r venv
  fi
  virtualenv venv --distribute --python python3
  . venv/bin/activate
  pip install docopt
  bin/get_docker_image.py --tag=${SHA} --search-distance=40

  # Build packages again if needed.
  if [ ! -d dist ]; then
    mkdir dist
    cd ${BUILD_PATH}
    . ./bin/exports.sh
    bin/make-packages confrestapp,confrestapp-testapi,comprestapp
    bin/make-packages hmerestapp
    bin/make-upgrade-hci confrestapp,hmerestapp
  fi
  '''

  try {
    Boolean test_errored = False
    sh """

    . ./bin/exports.sh

    # Extract the version number to use. At this point, we either downloaded or built,
    # so it's just easier to parse out what is in dist/ than figure that out. We use
    # comprestapp for matching to avoid matching more than one (e.g. confrestapp, confrestapp-testapi).
    VERSION="\$(ls dist | grep 'upgrade-comprestapp*' | sed 's/upgrade-comprestapp-\\(.*\\).tar/\\1/')"
    echo "VERSION=\$VERSION"

    if [ -z "\$VERSION" ]; then
      die "VERSION not found. Aborting."
    fi

    if [ "${p.vlan}" = "untagged" ]; then
      NETWORKS="resources/fragments/cicd/${env.config_network}.json"
    else
      NETWORKS="resources/fragments/cicd/${env.config_network}-vlan.json"
    fi
    echo "NETWORKS=\$NETWORKS"

    OPTIONAL_PARMS=""
    # Handle optional parameters
    if [ "${p.vlan}" = "untagged" ]; then
      OPTIONAL_PARMS="\$OPTIONAL_PARMS --all-untagged"
    fi
    if [ "${p.join_create_vcenter}" = "join" ]; then
      OPTIONAL_PARMS="\$OPTIONAL_PARMS --vcenter-join"
    fi
    if ${p.skip_validation}; then
      OPTIONAL_PARMS="\$OPTIONAL_PARMS --skip-validation"
    fi
    if ${p.aiq_opt_out}; then
      OPTIONAL_PARMS="\$OPTIONAL_PARMS --aiq-opt-out"
    fi
    if ${p.skip_rtfi}; then
      OPTIONAL_PARMS="\$OPTIONAL_PARMS --skip-rtfi"
    fi
    if ${p.skip_verify_build}; then
      OPTIONAL_PARMS="\$OPTIONAL_PARMS --skip-verify-build"
    fi
    if ${p.nde_reset}; then
      OPTIONAL_PARMS="\$OPTIONAL_PARMS --nde-reset"
    fi
    if ${p.skip_mark_hci}; then
      OPTIONAL_PARMS="\$OPTIONAL_PARMS --skip-mark-hci"
    fi
    if ${p.test_api}; then
      OPTIONAL_PARMS="\$OPTIONAL_PARMS --test-api"
    fi
    if ${p.skip_deploy}; then
      OPTIONAL_PARMS="\$OPTIONAL_PARMS --skip-deploy"
    fi
    if [ -n "${p.compute_ips}" ]; then
      OPTIONAL_PARMS="\$OPTIONAL_PARMS --compute-ips=${p.compute_ips}"
    fi
    if [ -n "${p.storage_ips}" ]; then
      OPTIONAL_PARMS="\$OPTIONAL_PARMS --storage-ips=${p.storage_ips}"
    fi
    if [ -n "${p.sfprime_branch}" ]; then
      OPTIONAL_PARMS="\$OPTIONAL_PARMS --sfprime-branch=${p.sfprime_branch}"
    fi
    if [ -n "${p.fqdn}" ]; then
      OPTIONAL_PARMS="\$OPTIONAL_PARMS --vcenter-fqdn=${p.fqdn}"
    fi
    if [ -n "${p.min_cable_setup}" ]; then
      OPTIONAL_PARMS="\$OPTIONAL_PARMS --min-cable-setup=${p.min_cable_setup}"
    fi
    if [ -n "${p.zero_conf_interfaces}" ]; then
      OPTIONAL_PARMS="\$OPTIONAL_PARMS --zero-conf-interfaces=${p.zero_conf_interfaces}"
    fi
    if ${p.use_simulator}; then
      OPTIONAL_PARMS="\$OPTIONAL_PARMS --use-simulator"
    fi

    echo "OPTIONAL_PARMS: \$OPTIONAL_PARMS"

    docker run --rm -i \
      -v "\$PWD/system_test/artifacts/":/sfprime-auto/artifacts \
      -v "\$PWD/system_test":/sfprime-auto \
      ${p.dockerhost}/sfprime-auto:latest ./scripts/hci-vl.sh python2 \
      /sfprime-auto/scripts/check_out_resources.py \
      --output-file=/sfprime-auto/artifacts/${p.stagename_short}_resources.json \
      --yaml-file=/sfprime-auto/resources/archetypes/${p.resources_yaml} \
      --username=$NGRM_CREDS_USR --password=$NGRM_CREDS_PSW \
      --test-path=$RUN_DISPLAY_URL \
      --test-stage=\"$STAGE_NAME\" \
      --vcenter-version=${p.vcenter_version} \
      --vcenter-vswitch=${p.vcenter_vswitch_type} \
      --storage-iso=\${ELEMENT_BASE_ISO} \
      --compute-iso=\${COMPUTE_BASE_ISO} \
      --storage-package=./dist/upgrade-confrestapp-\${VERSION}.tar \
      --compute-package=./dist/upgrade-comprestapp-\${VERSION}.tar \
      \$OPTIONAL_PARMS

    # Run Test Suite
    docker run --rm -i -v "\$PWD/system_test/artifacts/":/sfprime-auto/artifacts/ \
      -v "\$PWD/system_test":/sfprime-auto \
      -v "\$PWD/dist":/sfprime-auto/dist \
      -v /var/lib/jenkins/.ssh/:/root/.ssh/ ${p.dockerhost}/sfprime-auto:latest ./scripts/hci-vl.sh \
      vl run \
      --resources /sfprime-auto/artifacts/${p.stagename_short}_resources.json \
      --resources-class hciutils.models.suite_resources.SuiteResources ${p.test_suite}

    """
  } catch(outer_error) {
    test_errored = True
    echo 'Original test failure before resource health update and cleanup was: ' + outer_error.toString()
    throw_error = outer_error

    try {
      sh """
      echo 'Attempting to change health state of checked out resources to indicate test failure'
      # Set Resources Health to Tests Failed
      docker run --rm -i \
        -v "\$PWD/system_test/artifacts/":/sfprime-auto/artifacts \
        -v "\$PWD/system_test":/sfprime-auto \
        ${p.dockerhost}/sfprime-auto:latest ./scripts/hci-vl.sh python2 \
        /sfprime-auto/scripts/update_resources_health.py \
        --resource-file=/sfprime-auto/artifacts/${p.stagename_short}_resources.json \
        --health=test_failed --username=$NGRM_CREDS_USR --password=$NGRM_CREDS_PSW
      """
    } catch(health_state_error) {
      echo 'Failed to change resource health to Tests Failed with error: ' + health_state_error.toString()
    }
  } finally {
    try {
        sh """
        # Free resources and check them back in
        docker run --rm -i \
          -v "\$PWD/system_test/artifacts/":/sfprime-auto/artifacts \
          -v "\$PWD/system_test":/sfprime-auto \
          ${p.dockerhost}/sfprime-auto:latest ./scripts/hci-vl.sh python2 \
          /sfprime-auto/scripts/free_resources.py \
          --resource-file=/sfprime-auto/artifacts/${p.stagename_short}_resources.json \
          --remainder-file=/sfprime-auto/artifacts/${p.stagename_short}_remainder.json \
          --username=$NGRM_CREDS_USR --password=$NGRM_CREDS_PSW
        """
    } catch(check_in_error) {
        echo 'Failed to check resources back in with error: ' + check_in_error.toString()
        // if do not have error from test above, remember this error
        if ( !test_errored ) {
            test_errored = True
            throw_error = check_in_error
    }

    // change ownership to be able to archive
    helper.changeWSOwner(JENKINS_SLAVE_UID, JENKINS_SLAVE_GID)
    archiveArtifacts 'system_test/artifacts/**/*'

    // Cleanup
    sh '''
      . ./bin/exports.sh
      bin/run-in-container --docker_image=${SFPRIME_BUILD_IMAGE} --workdir=/temp rm -rf system_test/artifacts/*
    '''

    // from all activity above, if any error worth throwing occurred, throw the biggest
    if ( test_errored ) throw throw_error
  }
}

// Happy Path -  6.5, VDS, comp_d, tagged.
def HappyPath_65_VDS_compD_tagged(yaml_file, stagename_short='hp65vdscompdtagged')
{
  def parameters = helper.makeparamscopy()
  parameters['min_cable_setup'] ='compute'
  parameters['vlan'] = 'tagged'
  parameters['zero_conf_interfaces'] = 'management,iscsi'
  parameters['vcenter_version'] = '6.5'
  parameters['vcenter_vswitch_type'] = 'VDS'
  parameters['resources_yaml'] = yaml_file
  parameters['stagename_short'] = stagename_short

  runHappyPath(parameters)
}

// Happy Path -  6.7, VSS, comp_a, untagged
def HappyPath_67_VSS_compA_untagged(yaml_file, stagename_short='hp67vsscompauntagged')
{
  def parameters = helper.makeparamscopy()
  parameters['vlan'] = 'untagged'
  parameters['vcenter_version'] = '6.7'
  parameters['vcenter_vswitch_type'] = 'VSS'
  parameters['resources_yaml'] = yaml_file
  parameters['stagename_short'] = stagename_short

  runHappyPath(parameters)
}

// Happy Path -  6.7, VSS, comp_a, tagged
def HappyPath_67_VSS_compA_tagged(yaml_file, stagename_short='hp67vsscompatagged')
{
  def parameters = helper.makeparamscopy()
  parameters['vlan'] = 'tagged'
  parameters['vcenter_version'] = '6.7'
  parameters['vcenter_vswitch_type'] = 'VSS'
  parameters['resources_yaml'] = yaml_file
  parameters['stagename_short'] = stagename_short

  runHappyPath(parameters)
}

// Happy Path -  6.7, VDS, comp_d, tagged, no zc
def HappyPath_67_VSS_compD_tagged_no_zc(yaml_file, stagename_short='hp67vsscompdtaggednozc')
{
  def parameters = helper.makeparamscopy()
  parameters['min_cable_setup'] ='compute'
  parameters['vlan'] = 'tagged'
  parameters['vcenter_version'] = '6.7'
  parameters['vcenter_vswitch_type'] = 'VDS'
  parameters['resources_yaml'] = yaml_file
  parameters['stagename_short'] = stagename_short

  runHappyPath(parameters)
}

return this
