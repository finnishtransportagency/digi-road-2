(function(root) {
  root.NavigationPanel = {
    initialize: initialize
  };

  function initialize(container, searchBox, assetElements) {
    container.append('<div class="navigation-panel"></div>');

    var navigationPanel = $('.navigation-panel');

    navigationPanel.append(searchBox.element);

    _.forEach(assetElements, function(asset) {
      navigationPanel.append(asset.element.hide());
    });

    var assetControls = _.chain(assetElements)
      .map(function(asset) {
        return [asset.layerName, asset.element];
      })
      .zipObject()
      .value();

    eventbus.on('layer:selected', function selectLayer(layer, previouslySelectedLayer) {
      var previousControl = assetControls[previouslySelectedLayer];
      if (previousControl) previousControl.hide();
      assetControls[layer].show();
    });
  }
})(this);
