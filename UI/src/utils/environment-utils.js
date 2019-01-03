(function(root) {
  var urlParts = function() {
    var url = window.location.href.split('/');
    return _.filter(url, function(urlPart) { return !_.isEmpty(urlPart); });
  };

  var name = function() {
    var environmentName = {
      'extranet.vayla.fi': 'production',
      'testiextranet.vayla.fi': 'integration',
      'apptest.vayla.fi': 'training',
      'devtest.vayla.fi': 'staging'
    };

    return environmentName[urlParts()[1]] || 'unknown';
  };

  var urlPath = function() {
    var urlWithoutResource = _.initial(urlParts());
    return _.head(urlWithoutResource) + '//' + _.tail(urlWithoutResource).join('/');
  };

  // Environment name shown next to Digiroad logo
  var localizedName = function() {
    var localizedEnvironmentName = {
      integration: 'Integraatiotestiympäristö',
      production: '',
      training: 'Koulutusympäristö',
      staging: 'Testiympäristö',
      unknown: 'Kehitysympäristö'
    };
    return localizedEnvironmentName[Environment.name()];
  };

  root.Environment = {
    name: name,
    urlPath: urlPath,
    localizedName: localizedName
  };
}(this));